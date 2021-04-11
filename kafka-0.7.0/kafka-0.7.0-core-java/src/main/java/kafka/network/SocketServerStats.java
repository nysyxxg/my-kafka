package kafka.network;

import kafka.api.RequestKeys;
import kafka.utils.SnapshotStats;
import kafka.utils.SystemTime;
import kafka.utils.Time;

public class SocketServerStats implements SocketServerStatsMBean {
    private Long monitorDurationNs;
    private Time time;
    
    public SocketServerStats(Long monitorDurationNs, Time time) {
        this.monitorDurationNs = monitorDurationNs;
        this.time = time;
    }
    
    public SocketServerStats(Long monitorDurationNs) {
        this(monitorDurationNs, new SystemTime());
    }
    
    SnapshotStats produceTimeStats = new SnapshotStats(monitorDurationNs);
    SnapshotStats fetchTimeStats = new SnapshotStats(monitorDurationNs);
    SnapshotStats produceBytesStats = new SnapshotStats(monitorDurationNs);
    SnapshotStats fetchBytesStats = new SnapshotStats(monitorDurationNs);
    
    public void recordRequest(Short requestTypeId, Long durationNs) {
        
        if (requestTypeId == RequestKeys.Produce || requestTypeId == RequestKeys.MultiProduce) {
            produceTimeStats.recordRequestMetric(durationNs);
        } else if (requestTypeId == RequestKeys.Fetch || requestTypeId == RequestKeys.MultiFetch) {
            fetchTimeStats.recordRequestMetric(durationNs);
        }
    }
    
    public void recordBytesWritten(int bytes) {
        fetchBytesStats.recordRequestMetric((long) bytes);
    }
    
    public void recordBytesRead(int bytes) {
        produceBytesStats.recordRequestMetric((long) bytes);
    }
    
    public Double getProduceRequestsPerSecond() {
        return produceTimeStats.getRequestsPerSecond();
    }
    
    public Double getFetchRequestsPerSecond() {
        return fetchTimeStats.getRequestsPerSecond();
    }
    
    public Double getAvgProduceRequestMs() {
        return produceTimeStats.getAvgMetric() / (1000.0 * 1000.0);
    }
    
    public Double getMaxProduceRequestMs() {
        return produceTimeStats.getMaxMetric() / (1000.0 * 1000.0);
    }
    
    public Double getAvgFetchRequestMs() {
        return fetchTimeStats.getAvgMetric() / (1000.0 * 1000.0);
    }
    
    public Double getMaxFetchRequestMs() {
        return fetchTimeStats.getMaxMetric() / (1000.0 * 1000.0);
    }
    
    public Double getBytesReadPerSecond() {
        return produceBytesStats.getAvgMetric();
    }
    
    public Double getBytesWrittenPerSecond() {
        return fetchBytesStats.getAvgMetric();
    }
    
    public Long getNumFetchRequests() {
        return fetchTimeStats.getNumRequests();
    }
    
    public Long getNumProduceRequests() {
        return produceTimeStats.getNumRequests();
    }
    
    public Long getTotalBytesRead() {
        return produceBytesStats.getTotalMetric();
    }
    
    public Long getTotalBytesWritten() {
        return fetchBytesStats.getTotalMetric();
    }
    
    public Long getTotalFetchRequestMs() {
        return fetchTimeStats.getTotalMetric();
    }
    
    public Long getTotalProduceRequestMs() {
        return produceTimeStats.getTotalMetric();
    }
    
}
