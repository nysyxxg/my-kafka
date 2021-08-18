package kafka.producer.async;

public interface AsyncProducerStatsMBean {
    
    public int getAsyncProducerQueueSize();
    public int  getAsyncProducerDroppedEvents();
    
}
