package kafka.producer;

public interface SyncProducerStatsMBean {
    
    Double getProduceRequestsPerSecond();
    Double getAvgProduceRequestMs();
    Double getMaxProduceRequestMs();
    Long getNumProduceRequests();
    
}
