package kafka.consumer;

import kafka.common.ConsumerTimeoutException;
import kafka.message.MessageAndOffset;
import kafka.serializer.Decoder;
import kafka.utils.IteratorTemplate;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


public class ConsumerIterator<T> extends IteratorTemplate<T> {
    
    private Logger logger = Logger.getLogger(ConsumerIterator.class);
    private Iterator<MessageAndOffset> current  = null;
    private FetchedDataChunk currentDataChunk  = null;
    private PartitionTopicInfo currentTopicInfo   = null;
    private Long consumedOffset = -1L;
    
    String topic;
    BlockingQueue<FetchedDataChunk> channel;
    int consumerTimeoutMs;
    Decoder<T> decoder;
    
    public ConsumerIterator (String topic, BlockingQueue<FetchedDataChunk> channel,
                 int consumerTimeoutMs, Decoder<T> decoder ){
        this.channel = channel;
        this.topic = topic ;
        this.consumerTimeoutMs = consumerTimeoutMs;
        this.decoder = decoder;
    }
    
    @Override
    public T  next()  {
        T decodedMessage = super.next();
        if(consumedOffset < 0) {
            throw new IllegalStateException("Offset returned by the message set is invalid %d".format(String.valueOf(consumedOffset)));
        }
        currentTopicInfo.resetConsumeOffset(consumedOffset);
        if(logger.isTraceEnabled()) {
            logger.trace("Setting consumed offset to %d".format(String.valueOf(consumedOffset)));
        }
        ConsumerTopicStat.getConsumerTopicStat(topic).recordMessagesPerTopic(1);
       return decodedMessage;
    }
    
    @Override
    protected T makeNext() throws Throwable {
        // if we don't have an iterator, get one
        if(current == null || !current.hasNext()) {
            if (consumerTimeoutMs < 0)
                currentDataChunk = channel.take();
            else {
                currentDataChunk = channel.poll(consumerTimeoutMs, TimeUnit.MILLISECONDS);
                if (currentDataChunk == null) {
                    throw new ConsumerTimeoutException();
                }
            }
            if(currentDataChunk.equals(ZookeeperConsumerConnector.shutdownCommand())) {
                if(logger.isDebugEnabled()) {
                    logger.debug("Received the shutdown command");
                }
                channel.offer(currentDataChunk);
                return allDone();
            } else {
                currentTopicInfo = currentDataChunk.topicInfo;
                if (currentTopicInfo.getConsumeOffset() != currentDataChunk.fetchOffset) {
                    logger.error("consumed offset: %d doesn't match fetch offset: %d for %s;\n Consumer may lose data"
                            .format(currentTopicInfo.getConsumeOffset() +"", currentDataChunk.fetchOffset, currentTopicInfo));
                    currentTopicInfo.resetConsumeOffset(currentDataChunk.fetchOffset);
                }
                current = currentDataChunk.messages.iterator();
            }
        }
        MessageAndOffset item = current.next();
        consumedOffset = item.offset;
        decoder.toEvent(item.message);
        
        return null;
    }
}
