package kafka.producer;

import kafka.utils.SnapshotStats;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

public class SyncProducerStats implements SyncProducerStatsMBean {
    
    private Logger logger = Logger.getLogger(getClass());
    private String kafkaProducerstatsMBeanName = "kafka:type=kafka.KafkaProducerStats";
    private static SnapshotStats produceRequestStats = new SnapshotStats();
    
    public SyncProducerStats() {
        try {
            Utils.registerMBean(this, kafkaProducerstatsMBeanName);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn(e.getMessage(), e);
        }
    }
    
   static void recordProduceRequest(Long requestNs) {
        produceRequestStats.recordRequestMetric(requestNs);
    }
    
    @Override
    public Double getProduceRequestsPerSecond() {
        return produceRequestStats.getRequestsPerSecond();
    }
    
    @Override
    public Double getAvgProduceRequestMs() {
        return produceRequestStats.getAvgMetric() / (1000.0 * 1000.0);
    }
    
    @Override
    public Double getMaxProduceRequestMs() {
        return produceRequestStats.getMaxMetric() / (1000.0 * 1000.0);
    }
    
    @Override
    public Long getNumProduceRequests() {
        return produceRequestStats.getNumRequests();
    }
    
    
}
