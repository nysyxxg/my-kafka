package kafka.utils;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SnapshotStats {
    private Long monitorDurationNs = 600L * 1000L * 1000L * 1000L;
    
    public SnapshotStats(Long monitorDurationNs) {
        this.monitorDurationNs = monitorDurationNs;
    }
    
    private Time time = new SystemTime();
    
    private AtomicReference complete = new AtomicReference(new Stats());
    private AtomicReference current = new AtomicReference(new Stats());
    private AtomicLong total = new AtomicLong(0);
    private AtomicLong numCumulatedRequests = new AtomicLong(0);
    
    public SnapshotStats() {
    
    }
    
    public void recordRequestMetric(Long requestNs) {
        Stats stats = (Stats) current.get();
        stats.add(requestNs);
        total.getAndAdd(requestNs);
        numCumulatedRequests.getAndAdd(1);
        Long ageNs = time.nanoseconds() - stats.start;
        // if the current stats are too old it is time to swap
        if (ageNs >= monitorDurationNs) {
            boolean swapped = current.compareAndSet(stats, new Stats());
            if (swapped) {
                complete.set(stats);
                stats.end.set(time.nanoseconds());
            }
        }
    }
    
    public void recordThroughputMetric(Long data) {
        Stats stats = (Stats) current.get();
        stats.addData(data);
        Long ageNs = time.nanoseconds() - stats.start;
        // if the current stats are too old it is time to swap
        if (ageNs >= monitorDurationNs) {
            boolean swapped = current.compareAndSet(stats, new Stats());
            if (swapped) {
                complete.set(stats);
                stats.end.set(time.nanoseconds());
            }
        }
    }
    
    public Long getNumRequests() {
        return numCumulatedRequests.get();
    }
    
    public Double getRequestsPerSecond() {
        Stats stats = (Stats) complete.get();
        return stats.numRequests / stats.durationSeconds();
    }
    
    public Double getThroughput() {
        Stats stats = (Stats) complete.get();
        return stats.totalData / stats.durationSeconds();
    }
    
    public Double getAvgMetric() {
        Stats stats = (Stats) complete.get();
        if (stats.numRequests == 0) {
            return Double.valueOf(0);
        } else {
            return Double.valueOf(stats.totalRequestMetric / stats.numRequests);
        }
    }
    
    public Long getTotalMetric() {
        return total.get();
    }
    
    public Double getMaxMetric() {
        Stats stats = (Stats) complete.get();
        return Double.valueOf(stats.maxRequestMetric);
    }
    
    class Stats {
        Long start = time.nanoseconds();
        AtomicLong end = new AtomicLong(-1);
        int numRequests = 0;
        Long totalRequestMetric = 0L;
        Long maxRequestMetric = 0L;
        Long totalData = 0L;
        private Object lock = new Object();
        
        void addData(Long data) {
            synchronized (lock) {
                totalData += data;
            }
        }
        
        void add(Long requestNs) {
            synchronized (lock) {
                numRequests += 1;
                totalRequestMetric += requestNs;
                maxRequestMetric = Math.max(maxRequestMetric, requestNs);
            }
        }
        
        Double durationSeconds() {
            return (end.get() - start) / (1000.0 * 1000.0 * 1000.0);
        }
        
        Double durationMs() {
            return (end.get() - start) / (1000.0 * 1000.0);
        }
        
        public Long getMaxRequestMetric() {
            return maxRequestMetric;
        }
        
        public void setMaxRequestMetric(Long maxRequestMetric) {
            this.maxRequestMetric = maxRequestMetric;
        }
    }
}