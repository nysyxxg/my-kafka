package kafka.message;

import kafka.utils.SnapshotStats;
import kafka.utils.Utils;
import org.apache.log4j.Logger;


interface LogFlushStatsMBean {
    Double getFlushesPerSecond();
    
    Double getAvgFlushMs();
    
    Long getTotalFlushMs();
    
    Double getMaxFlushMs();
    
    Long getNumFlushes();
}


public class LogFlushStats  implements LogFlushStatsMBean {
    
    private static SnapshotStats flushRequestStats = new SnapshotStats();
    
    private Logger logger = Logger.getLogger(getClass());
    private String LogFlushStatsMBeanName = "kafka:type=kafka.LogFlushStats";
    private LogFlushStats stats = new LogFlushStats();
    
    public LogFlushStats() {
        try {
            Utils.registerMBean(stats, LogFlushStatsMBeanName);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn(e.getMessage(), e);
        }
        
    }
    
    static void recordFlushRequest(Long requestMs) {
        //stats.recordFlushRequest(requestMs);
        flushRequestStats.recordRequestMetric(requestMs);
    }
    
    @Override
    public Double getFlushesPerSecond() {
        return flushRequestStats.getRequestsPerSecond();
    }
    
    @Override
    public Double getAvgFlushMs() {
        return  flushRequestStats.getAvgMetric();
    }
    
    @Override
    public Long getTotalFlushMs() {
        return flushRequestStats.getTotalMetric();
    }
    
    @Override
    public Double getMaxFlushMs() {
        return flushRequestStats.getMaxMetric();
    }
    
    @Override
    public Long getNumFlushes() {
        return flushRequestStats.getNumRequests();
    }
}
