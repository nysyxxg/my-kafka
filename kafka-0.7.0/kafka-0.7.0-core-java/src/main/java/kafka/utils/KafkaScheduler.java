package kafka.utils;

import org.apache.log4j.Logger;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class KafkaScheduler {
    private Logger logger = Logger.getLogger(getClass());
    
    private AtomicLong threadId = new AtomicLong(0);
    
    private int numThreads;
    private String baseThreadName;
    private Boolean isDaemon;
    
    private ScheduledThreadPoolExecutor executor;
    
    
    public KafkaScheduler(int numThreads, String baseThreadName, Boolean isDaemon) {
        this.numThreads = numThreads;
        this.baseThreadName = baseThreadName;
        this.isDaemon = isDaemon;
        
        // 初始化线程池
        this.executor = new ScheduledThreadPoolExecutor(numThreads, new ThreadFactory() {
            public Thread newThread(Runnable runnable) {
                Thread t = new Thread(runnable, baseThreadName + threadId.getAndIncrement());
                t.setDaemon(isDaemon);
                return t;
            }
        });
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }
    
    
    public void scheduleWithRate(UnitFunction fun, Long delayMs, Long periodMs) {
        executor.scheduleAtFixedRate(Utils.loggedRunnable(fun), delayMs, periodMs, TimeUnit.MILLISECONDS);
        
    }
    
    public void shutdownNow() {
        executor.shutdownNow();
        logger.info("force shutdown scheduler " + baseThreadName);
    }
    
    public void shutdown() {
        executor.shutdown();
        logger.info("shutdown scheduler " + baseThreadName);
    }
    
}
