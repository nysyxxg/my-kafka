package kafka.consumer;

import kafka.cluster.Partition;
import kafka.common.ErrorMapping;
import kafka.message.ByteBufferMessageSet;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PartitionTopicInfo {
    
    private Logger logger = Logger.getLogger(PartitionTopicInfo.class);
    
    String topic;
    int brokerId;
    Partition partition;
    private BlockingQueue<FetchedDataChunk> chunkQueue;
    private AtomicLong consumedOffset;
    private AtomicLong fetchedOffset;
    private AtomicInteger fetchSize;
    
    PartitionTopicInfo(String topic,
                       int brokerId,
                       Partition partition,
                       BlockingQueue<FetchedDataChunk> chunkQueue,
                       AtomicLong consumedOffset,
                       AtomicLong fetchedOffset,
                       AtomicInteger fetchSize) {
        this.topic = topic;
        this.brokerId = brokerId;
        this.partition = partition;
        this.chunkQueue = chunkQueue;
        this.consumedOffset = consumedOffset;
        this.fetchedOffset = fetchedOffset;
        this.fetchSize = fetchSize;
        
        if (logger.isDebugEnabled()) {
            logger.debug("initial consumer offset of " + this + " is " + consumedOffset);
            logger.debug("initial fetch offset of " + this + " is " + fetchedOffset);
        }
        
    }
    
    
    public void resetConsumeOffset(Long newConsumeOffset) {
        consumedOffset.set(newConsumeOffset);
        if (logger.isDebugEnabled()) {
            logger.debug("reset consume offset of " + this + " to " + newConsumeOffset);
        }
    }
    
    
    public void resetFetchOffset(Long newFetchOffset) {
        fetchedOffset.set(newFetchOffset);
        if (logger.isDebugEnabled()) {
            logger.debug("reset fetch offset of ( %s ) to %d".format(this.toString(), newFetchOffset));
        }
    }
    
    
    Long enqueue(ByteBufferMessageSet messages, Long fetchOffset) {
        Long size = messages.validBytes;
        if (size > 0) {
            // update fetched offset to the compressed data chunk size, not the decompressed message set size
            if (logger.isTraceEnabled())
                logger.trace("Updating fetch offset = " + fetchedOffset.get() + " with size = " + size);
            try {
                chunkQueue.put(new FetchedDataChunk(messages, this, fetchOffset));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            long newOffset = fetchedOffset.addAndGet(size);
            if (logger.isDebugEnabled()) {
                logger.debug("updated fetch offset of ( %s ) to %d".format(this.toString(), newOffset));
            }
        }
        return size;
    }
    
    /**
     * add an empty message with the exception to the queue so that client can see the error
     */
    void enqueueError(Throwable e, Long fetchOffset) {
        
        ByteBuffer buffer = ErrorMapping.EmptyByteBuffer;
        Long initialOffset = 0l;
        int errorCode = ErrorMapping.codeFor(e);
        ByteBufferMessageSet messages = null;
        try {
            messages = new ByteBufferMessageSet(buffer, initialOffset, errorCode);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        try {
            chunkQueue.put(new FetchedDataChunk(messages, this, fetchOffset));
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        }
    }
    
    public String toString() {
        return topic + ":" + partition.toString() + ": fetched offset = " + fetchedOffset.get() + ": consumed offset = " + consumedOffset.get();
    }
    
    public Long getConsumeOffset() {
        return consumedOffset.get();
    }
    
    public Long getFetchOffset() {
        return fetchedOffset.get();
    }
    
}
