package kafka.producer.async;

import kafka.producer.QueueItem;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncProducerStats<T> implements AsyncProducerStatsMBean {
    private AtomicInteger droppedEvents = new AtomicInteger(0);
    private AtomicInteger numEvents = new AtomicInteger(0);
    private LinkedBlockingQueue<QueueItem<T>> queue;
    
    public AsyncProducerStats(LinkedBlockingQueue<QueueItem<T>> queue) {
        this.queue = queue;
    }
    
    @Override
    public int getAsyncProducerQueueSize() {
        return queue.size();
    }
   
    @Override
    public int getAsyncProducerDroppedEvents() {
        return droppedEvents.get();
    }
    
    public int recordDroppedEvents() {
        return droppedEvents.getAndAdd(1);
    }
    
    public int recordEvent() {
        return numEvents.getAndAdd(1);
    }
    
}
