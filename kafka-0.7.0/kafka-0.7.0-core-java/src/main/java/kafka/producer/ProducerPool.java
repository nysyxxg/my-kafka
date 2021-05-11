package kafka.producer;

import kafka.api.ProducerRequest;
import kafka.cluster.Broker;
import kafka.cluster.Partition;
import kafka.common.InvalidConfigException;
import kafka.common.UnavailableProducerException;
import kafka.message.ByteBufferMessageSet;
import kafka.message.Message;
import kafka.message.NoCompressionCodec;
import kafka.producer.async.*;
import kafka.serializer.Encoder;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProducerPool<V> {
    
    private Logger logger = Logger.getLogger(ProducerPool.class);
    private ProducerConfig config;
    private Encoder<V> serializer;
    private ConcurrentMap<Integer, SyncProducer> syncProducers;
    private ConcurrentMap<Integer, AsyncProducer<V>> asyncProducers;
    private EventHandler<V> inputEventHandler = null;
    private CallbackHandler<V> cbkHandler = null;
    private EventHandler<V> eventHandler = null;
    private Boolean sync = true;
    
    ProducerPool(ProducerConfig config,
                 Encoder<V> serializer,
                 ConcurrentMap<Integer, SyncProducer> syncProducers,
                 ConcurrentMap<Integer, AsyncProducer<V>> asyncProducers,
                 EventHandler<V> inputEventHandler,
                 CallbackHandler<V> cbkHandler) {
        this.config = config;
        this.serializer = serializer;
        this.syncProducers = syncProducers;
        this.asyncProducers = asyncProducers;
        this.inputEventHandler = inputEventHandler;
        this.cbkHandler = cbkHandler;
        
        
        this.eventHandler = inputEventHandler;
        if (eventHandler == null) {
            eventHandler = new DefaultEventHandler(config, cbkHandler);
        }
        if (serializer == null) {
            throw new InvalidConfigException("serializer passed in is null!");
        }
        
        if (config.producerType.equals("sync")) {
            this.sync = true;
        } else if (config.producerType.equals("async")) {
            this.sync = false;
        }
//        else {
//            throw new InvalidConfigException("Valid values for producer.type are sync/async");
//        }
    }
    
    
    ProducerPool(ProducerConfig config, Encoder<V> serializer,
                 EventHandler<V> eventHandler, CallbackHandler<V> cbkHandler) {
        this(config, serializer,
                new ConcurrentHashMap<Integer, SyncProducer>(),
                new ConcurrentHashMap<Integer, AsyncProducer<V>>(),
                eventHandler, cbkHandler);
    }
    
    public ProducerPool(ProducerConfig config, Encoder<V> serializer) {
        this(config, serializer, new ConcurrentHashMap<Integer, SyncProducer>(),
                new ConcurrentHashMap<Integer, AsyncProducer<V>>(),
                (EventHandler) Utils.getObject(config.eventHandler),
                (CallbackHandler) Utils.getObject(config.cbkHandler));
    }
    
    
    public void addProducer(Broker broker) {
        Properties props = new Properties();
        props.put("host", broker.host);
        props.put("port", broker.port);
        props.putAll(config.props);
        if (sync) {
            SyncProducer producer = new SyncProducer(new SyncProducerConfig(props));
            logger.info("Creating sync producer for broker id = " + broker.id + " at " + broker.host + ":" + broker.port);
            syncProducers.put(broker.id, producer);
        } else {
            AsyncProducer producer = new AsyncProducer<V>(new AsyncProducerConfig(props),
                    new SyncProducer(new SyncProducerConfig(props)),
                    serializer,
                    eventHandler, config.eventHandlerProps,
                    cbkHandler, config.cbkHandlerProps);
            producer.start();
            logger.info("Creating async producer for broker id = " + broker.id + " at " + broker.host + ":" + broker.port);
            asyncProducers.put(broker.id, producer);
        }
    }
    
    
    public void send(ProducerPoolData<V> poolData[]) throws Throwable {
        Set<Integer> distinctBrokers = new TreeSet<Integer>();
        for (ProducerPoolData pd : poolData) {
            distinctBrokers.add(pd.getBidPid().getBrokerId());
        }
        ProducerPoolData[] remainingRequests = poolData;
        for (Integer bid : distinctBrokers) {
            List<ProducerPoolData> eqRemainingRequests1 = new ArrayList<>();
            List<ProducerPoolData> notEqRemainingRequests2 = new ArrayList<>();
            for (ProducerPoolData data : remainingRequests) {
                if (data.getBidPid().getBrokerId() == bid) {
                    eqRemainingRequests1.add(data);
                } else {
                    notEqRemainingRequests2.add(data);
                }
            }
            //val requestsForThisBid = remainingRequests partition (_.getBidPid.brokerId == bid);
            List<ProducerPoolData> remainingRequests2 = notEqRemainingRequests2;
            
            if (sync) {
                
                List<ProducerRequest> producerRequests = new ArrayList<>();
                for (ProducerPoolData req : eqRemainingRequests1) {
                    List<Message> msgList = new ArrayList();
                    for (Object data : req.getData()) {
                        msgList.add(serializer.toMessage((V) data));
                    }
                    ByteBufferMessageSet byteBufferMessageSet = new ByteBufferMessageSet(config.compressionCodec, msgList);
                    producerRequests.add(new ProducerRequest(req.getTopic(), req.getBidPid().partId, byteBufferMessageSet));
                }
                logger.debug("Fetching sync producer for broker id: " + bid);
                SyncProducer producer = syncProducers.get(bid);
                if (producer != null) {
                    if (producerRequests.size() > 1) {
                        producer.multiSend((ProducerRequest[]) producerRequests.toArray());
                    } else {
                        ProducerRequest producerRequest = producerRequests.get(0);
                        producer.send(producerRequest.topic, producerRequest.partition, producerRequest.messages);
                        if (config.compressionCodec instanceof NoCompressionCodec) {
                            logger.debug("Sending message to broker " + bid);
                        } else {
                            logger.debug("Sending compressed messages to broker " + bid);
                        }
                    }
                } else {
                    throw new UnavailableProducerException("Producer pool has not been initialized correctly. " +
                            "Sync Producer for broker " + bid + " does not exist in the pool");
                }
            } else {
                logger.debug("Fetching async producer for broker id: " + bid);
                AsyncProducer<V> producer = asyncProducers.get(bid);
                if (producer != null) {
                    for (ProducerPoolData req : eqRemainingRequests1) {
                        List<ProducerPoolData> data = req.getData();
                        for (ProducerPoolData d : data) {
                            producer.send(req.getTopic(), (V) d, req.getBidPid().partId);
                        }
                    }
                    if (logger.isDebugEnabled()) {
                        if (config.compressionCodec instanceof NoCompressionCodec) {
                            logger.debug("Sending message");
                        } else {
                            logger.debug("Sending compressed messages");
                        }
                    }
                } else {
                    throw new UnavailableProducerException("Producer pool has not been initialized correctly. " +
                            "Async Producer for broker " + bid + " does not exist in the pool");
                }
            }
        }
    }
    
    
    public void close() {
        String type = config.producerType;
        if (type.equals("sync")) {
            logger.info("Closing all sync producers");
            Iterator<SyncProducer> iter = syncProducers.values().iterator();
            while (iter.hasNext()) {
                iter.next().close();
            }
        } else if (type.equals("async")) {
            logger.info("Closing all async producers");
            Iterator<AsyncProducer<V>> iter = asyncProducers.values().iterator();
            while (iter.hasNext()) {
                iter.next().close();
            }
        }
    }
    
    
    public ProducerPoolData getProducerPoolData(String topic, Partition bidPid, List<V> data) {
        return new ProducerPoolData<V>(topic, bidPid, data);
    }
    
    class ProducerPoolData<V> {
        String topic;
        Partition bidPid;
        List<V> data;
        
        ProducerPoolData(String topic,
                         Partition bidPid,
                         List<V> data) {
            this.bidPid = bidPid;
            this.topic = topic;
            this.data = data;
        }
        
        public String getTopic() {
            return topic;
        }
        
        public Partition getBidPid() {
            return bidPid;
        }
        
        public List<V> getData() {
            return data;
        }
    }
    
    
}
