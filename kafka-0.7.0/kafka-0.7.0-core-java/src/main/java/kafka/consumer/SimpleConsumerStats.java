package kafka.consumer;

import kafka.utils.SnapshotStats;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

interface SimpleConsumerStatsMBean {
    Double getFetchRequestsPerSecond();
    
    Double getAvgFetchRequestMs();
    
    Double getMaxFetchRequestMs();
    
    Long getNumFetchRequests();
    
    Double getConsumerThroughput();
}


public class SimpleConsumerStats implements SimpleConsumerStatsMBean {
    private static SnapshotStats fetchRequestStats = new SnapshotStats();
    private Logger logger = Logger.getLogger(getClass());
    private String simpleConsumerstatsMBeanName = "kafka:type=kafka.SimpleConsumerStats";
    
    private SimpleConsumerStats stats = new SimpleConsumerStats();
    
    public SimpleConsumerStats() {
        Utils.registerMBean(stats, simpleConsumerstatsMBeanName);
    }
    
    
    @Override
    public Double getFetchRequestsPerSecond() {
        return fetchRequestStats.getRequestsPerSecond();
    }
    
    @Override
    public Double getAvgFetchRequestMs() {
        return  fetchRequestStats.getAvgMetric() / (1000.0 * 1000.0);
    }
    
    @Override
    public Double getMaxFetchRequestMs() {
        return fetchRequestStats.getMaxMetric() / (1000.0 * 1000.0);
    }
    
    @Override
    public Long getNumFetchRequests() {
        return fetchRequestStats.getNumRequests();
    }
    
    @Override
    public Double getConsumerThroughput() {
        return fetchRequestStats.getThroughput();
    }
    
    public  static void recordFetchRequest(Long requestMs) {
        fetchRequestStats.recordRequestMetric(requestMs);
    }
    
    public static  void recordConsumptionThroughput(Long data) {
        fetchRequestStats.recordThroughputMetric(data);
    }
    
}
