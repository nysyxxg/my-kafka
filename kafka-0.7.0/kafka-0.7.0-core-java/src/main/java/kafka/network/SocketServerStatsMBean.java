package kafka.network;

public interface SocketServerStatsMBean {
    
    Double getProduceRequestsPerSecond();
    Double getFetchRequestsPerSecond();
    Double getAvgProduceRequestMs();
    Double getMaxProduceRequestMs();
    Double getAvgFetchRequestMs();
    Double getMaxFetchRequestMs();
    Double getBytesReadPerSecond();
    Double getBytesWrittenPerSecond();
    Long getNumFetchRequests();
    Long getNumProduceRequests();
    Long getTotalBytesRead();
    Long getTotalBytesWritten();
    Long getTotalFetchRequestMs();
    Long getTotalProduceRequestMs();
    
}
