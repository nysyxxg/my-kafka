package kafka.server;

import kafka.api.*;
import kafka.common.ErrorMapping;
import kafka.log.Log;
import kafka.log.LogManager;
import kafka.message.MessageSet;
import kafka.network.Receive;
import kafka.network.Send;
import kafka.utils.SystemTime;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KafkaRequestHandlers {
    private Logger logger = Logger.getLogger(KafkaRequestHandlers.class);
    private Logger requestLogger = Logger.getLogger("kafka.request.logger");
    private LogManager logManager;
    
    public KafkaRequestHandlers(LogManager logManager) {
        this.logManager = logManager;
    }
    
    public Send handlerFor(Short requestTypeId, Receive receive) {
        if (requestTypeId == RequestKeys.Produce) {
            handleProducerRequest(receive);
        } else if (requestTypeId == RequestKeys.Fetch) {
            handleFetchRequest(receive);
        } else if (requestTypeId == RequestKeys.MultiFetch) {
            handleMultiFetchRequest(receive);
        } else if (requestTypeId == RequestKeys.MultiProduce) {
            handleMultiProducerRequest(receive);
        } else if (requestTypeId == RequestKeys.Offsets) {
            handleOffsetRequest(receive);
        } else {
            throw new IllegalStateException("No mapping found for handler id " + requestTypeId);
        }
        return null;
    }
    
    
    Send handleOffsetRequest(Receive request) {
        OffsetRequest offsetRequest = OffsetRequest.readFrom(request.buffer());
        if (requestLogger.isTraceEnabled())
            requestLogger.trace("Offset request " + offsetRequest.toString());
        Log log = logManager.getOrCreateLog(offsetRequest.topic, offsetRequest.partition);
        Long[] offsets = log.getOffsetsBefore(offsetRequest);
        OffsetArraySend response = new OffsetArraySend(offsets);
        return response;
    }
    
    
    Send handleMultiProducerRequest(Receive receive) {
        MultiProducerRequest request = MultiProducerRequest.readFrom(receive.buffer());
        if (requestLogger.isTraceEnabled()) {
            requestLogger.trace("Multiproducer request " + request.toString());
        }
        
        for (ProducerRequest producerRequest : request.produces) {
            handleProducerRequest(producerRequest, "MultiProducerRequest");
        }
        return null;
    }
    
    
    Send handleMultiFetchRequest(Receive request) {
        MultiFetchRequest multiFetchRequest = MultiFetchRequest.readFrom(request.buffer());
        if (requestLogger.isTraceEnabled()) {
            requestLogger.trace("Multifetch request");
        }
        FetchRequest fetches[] = multiFetchRequest.fetches;
        
        for (FetchRequest req : fetches) {
            requestLogger.trace(req.toString());
        }
        List<MessageSetSend> responses = new ArrayList<>();
        
        for (FetchRequest fetch : fetches) {
            responses.add(readMessageSet(fetch));
        }
        return new MultiMessageSetSend(responses);
    }
    
    
    Send handleFetchRequest(Receive request) {
        FetchRequest fetchRequest = FetchRequest.readFrom(request.buffer());
        if (requestLogger.isTraceEnabled())
            requestLogger.trace("Fetch request " + fetchRequest.toString());
        return readMessageSet(fetchRequest);
    }
    
    
    public Send handleProducerRequest(Receive receive) {
        Long sTime = System.currentTimeMillis();
        ProducerRequest request = ProducerRequest.readFrom(receive.buffer());
        
        if (requestLogger.isTraceEnabled()) {
            requestLogger.trace("Producer request " + request.toString());
        }
        handleProducerRequest(request, "ProduceRequest");
        if (logger.isDebugEnabled()) {
            logger.debug("kafka produce time " + (SystemTime.getMilliseconds() - sTime) + " ms");
        }
        return null;
    }
    
    private void handleProducerRequest(ProducerRequest request, String requestHandlerName) {
        int randomP = logManager.chooseRandomPartition(request.topic);
        int partition = request.getTranslatedPartition(randomP);
        try {
            logManager.getOrCreateLog(request.topic, partition).append(request.messages);
            if (logger.isTraceEnabled())
                logger.trace(request.messages.sizeInBytes() + " bytes written to logs.");
        } catch (Exception e) {
            logger.error("Error processing " + requestHandlerName + " on " + request.topic + ":" + partition, e);
            logger.fatal("Halting due to unrecoverable I/O error while handling producer request: " + e.getMessage(), e);
            Runtime.getRuntime().halt(1);
        }
    }
    
    private MessageSetSend readMessageSet(FetchRequest fetchRequest) {
        MessageSetSend response = null;
        try {
            logger.trace("Fetching log segment for topic = " + fetchRequest.topic + " and partition = " + fetchRequest.partition);
            Log log = logManager.getOrCreateLog(fetchRequest.topic, fetchRequest.partition);
            response = new MessageSetSend(log.read(fetchRequest.offset, (long) fetchRequest.maxSize));
        } catch (Exception e) {
            logger.error("error when processing request " + fetchRequest, e);
            try {
                response = new MessageSetSend(MessageSet.Empty, ErrorMapping.codeFor(e.getClass().newInstance().getCause()));
            } catch (IOException e1) {
                e1.printStackTrace();
            } catch (InstantiationException e1) {
                e1.printStackTrace();
            } catch (IllegalAccessException e1) {
                e1.printStackTrace();
            }
        }
        return response;
    }
    
    
}
