package kafka.log;

import clover.org.apache.commons.lang.StringUtils;
import kafka.common.InvalidPartitionException;
import kafka.common.InvalidTopicException;
import kafka.server.KafkaConfig;
import kafka.server.KafkaZooKeeper;
import kafka.utils.*;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class LogManager {
    
    private Logger logger = Logger.getLogger(LogManager.class);
    private Object logCreationLock = new Object();
    private Random random = new java.util.Random();
    private KafkaZooKeeper kafkaZookeeper = null;
    private Thread zkActorThread = null;
    private KafkaScheduler logFlusherScheduler = new KafkaScheduler(1, "kafka-logflusher-", false);
    
    private Map<String, Integer> logFlushIntervalMap = null;
    private Map<String, Long> logRetentionMSMap = null;
    private int logRetentionSize;
    private CountDownLatch startupLatch = null;
    private File logDir = null;
    private int numPartitions;
    private Long maxSize = null;
    private int flushInterval;
    private Map<String, Integer> topicPartitionsMap;
    
    public KafkaConfig config;
    public KafkaScheduler scheduler;
    public Time time;
    public Long logCleanupIntervalMs;
    public Long logCleanupDefaultAgeMs;
    public Boolean needRecovery;
    
    private Pool logs = new Pool<String, Pool<Integer, Log>>();
    
    private volatile String topic = "";
    private volatile String state = "run";
    
    public LogManager(KafkaConfig config,
                      KafkaScheduler scheduler,
                      Time time,
                      Long logCleanupIntervalMs,
                      Long logCleanupDefaultAgeMs,
                      Boolean needRecovery) {
        this.config = config;
        this.scheduler = scheduler;
        this.time = time;
        this.logCleanupIntervalMs = logCleanupIntervalMs;
        this.logCleanupDefaultAgeMs = logCleanupDefaultAgeMs;
        this.needRecovery = needRecovery;
        
        
        this.logFlushIntervalMap = config.flushIntervalMap;
        this.logRetentionMSMap = getLogRetentionMSMap(config.logRetentionHoursMap);
        this.logRetentionSize = config.logRetentionSize;
        if (config.enableZookeeper) {
            this.startupLatch = new CountDownLatch(1);
        }
        this.logDir = new File(config.logDir);
        this.numPartitions = config.numPartitions;
        this.maxSize = Long.parseLong(config.logFileSize + "");
        this.flushInterval = config.flushInterval;
        this.topicPartitionsMap = config.topicPartitionsMap;
        
        // 初始化
        init();
    }
    
    
    void init() {
        if (!logDir.exists()) {
            logger.info("No log directory found, creating '" + logDir.getAbsolutePath() + "'");
            logDir.mkdirs();
        }
        if (!logDir.isDirectory() || !logDir.canRead())
            throw new IllegalArgumentException(logDir.getAbsolutePath() + " is not a readable log directory.");
        File subDirs[] = logDir.listFiles();
        if (subDirs != null) {
            for (File dir : subDirs) {
                if (!dir.isDirectory()) {
                    logger.warn("Skipping unexplainable file '" + dir.getAbsolutePath() + "'--should it be there?");
                } else {
                    logger.info("Loading log '" + dir.getName() + "'");
                    Log log = new Log(dir, maxSize, flushInterval, needRecovery);
                    Tuple2<String, Integer> topicPartion = Utils.getTopicPartition(dir.getName());
                    logs.putIfNotExists(topicPartion._1, new Pool<Integer, Log>());
                    Pool<Integer, Log> parts = (Pool<Integer, Log>) logs.get(topicPartion._1);
                    parts.put(topicPartion._2, log);
                }
            }
        }
        
        /* Schedule the cleanup task to delete old logs */
        if (scheduler != null) {
            logger.info("定时清除任务以删除旧日志 starting log cleaner every " + logCleanupIntervalMs + " ms");
            UnitFunction function = new UnitFunction() {
                @Override
                public void call() {
                    logger.info("定时清理日志线程：" + Thread.currentThread().getName());
                    cleanupLogs();
                }
                
                @Override
                public Object call(Object v) {
                    return null;
                }
            };
            scheduler.scheduleWithRate(function, 60 * 1000L, logCleanupIntervalMs);
        }
        
        if (config.enableZookeeper) {
            kafkaZookeeper = new KafkaZooKeeper(config, this);
            kafkaZookeeper.startup();

//            zkActorThread = new Thread() {
//                @Override
//                public void run() {
//                    while (true) {
//                        // 监听topic信息
//                        if (!StringUtils.isEmpty(topic)) {
//                            kafkaZookeeper.registerTopicInZk(topic);
//                        }
//
//                        if (state.equals("stop")) {
//                            Thread.currentThread().interrupt();
//                            logger.info("zkActor stopped");
//                            System.exit(0);
//                        }
//                    }
//                }
//            };
//            zkActorThread.start();
        }
        
    }
    
    
    private Map<String, Long> getLogRetentionMSMap(Map<String, Integer> logRetentionHourMap) {
        Map<String, Long> ret = new HashMap<String, Long>();
        for (String topic : logRetentionHourMap.keySet()) {
            Integer hour = logRetentionHourMap.get(topic);
            ret.put(topic, hour * 60 * 60 * 1000L);
        }
        return ret;
    }
    
    void cleanupLogs() {
        logger.debug("Beginning log cleanup...");
        Iterator<Log> iter = getLogIterator();
        int total = 0;
        Long startMs = time.milliseconds();
        while (iter.hasNext()) {
            Log log = iter.next();
            logger.debug("Garbage collecting '" + log.name + "'");
            total += cleanupExpiredSegments(log) + cleanupSegmentsToMaintainSize(log);
        }
        logger.debug("Log cleanup completed. " + total + " files deleted in " + (time.milliseconds() - startMs) / 1000 + " seconds");
    }
    
    private Integer cleanupSegmentsToMaintainSize(Log log) {
        if (logRetentionSize < 0 || log.size() < logRetentionSize) return 0;
        long diff = log.size() - logRetentionSize;
        
        
        List<LogSegment> toBeDeleted = log.markDeletedWhileV2(diff);
        int total = deleteSegments(log, toBeDeleted);
        return total;
    }
    
    
    private Integer cleanupExpiredSegments(Log log) {
        Long startMs = time.milliseconds();
        String topic = Utils.getTopicPartition(log.dir.getName())._1;
        Long logCleanupThresholdMS = logRetentionMSMap.get(topic);
        if (logCleanupThresholdMS == null) {
            logCleanupThresholdMS = this.logCleanupDefaultAgeMs;
        }
        // startMs - _.file.lastModified > logCleanupThresholdMS
        List<LogSegment> toBeDeleted = log.markDeletedWhile(startMs, logCleanupThresholdMS);
        int total = deleteSegments(log, toBeDeleted);
        return total;
    }
    
    private int deleteSegments(Log log, List<LogSegment> segments) {
        int total = 0;
        for (LogSegment segment : segments) {
            logger.info("Deleting log segment " + segment.file.getName() + " from " + log.name);
            
            try {
                segment.messageSet.close();
            } catch (IOException e) {
                e.printStackTrace();
                logger.warn(e.getMessage(), e);
            }
            
            if (!segment.file.delete()) {
                logger.warn("Delete failed.");
            } else {
                total += 1;
            }
        }
        return total;
    }
    
    
    private Iterator<Log> getLogIterator() {
        return new IteratorTemplate<Log>() {
            Iterator<Pool> partsIter = logs.values.iterator();
            Iterator<Log> logIter = null;
            
            @Override
            protected Log makeNext() throws Throwable {
                while (true) {
                    if (logIter != null && logIter.hasNext())
                        return logIter.next();
                    if (!partsIter.hasNext())
                        return allDone();
                    Pool pool = partsIter.next();
                    logIter = pool.values.iterator();
                }
                // should never reach here
            }
        };
    }
    
    
    void test(Boolean assertion) {
        if (!assertion)
            throw new java.lang.AssertionError("assertion failed");
    }
    
    
    public Map getTopicPartitionsMap() {
        return topicPartitionsMap;
    }
    
    private void awaitStartup() {
        if (config.enableZookeeper)
            try {
                startupLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
    }
    
    public Log getOrCreateLog(String topic, int partition) {
        awaitStartup();
        if (topic.length() <= 0) {
            throw new InvalidTopicException("topic name can't be empty");
        }
        Integer p = topicPartitionsMap.get(topic);
        if (p == null) {
            p = numPartitions;
        }
        if (partition < 0 || partition > p) {
            logger.warn("Wrong partition " + partition + " valid partitions (0," + (p - 1) + ")");
            throw new InvalidPartitionException("wrong partition " + partition);
        }
        boolean hasNewTopic = false;
        Pool<Integer, Log> parts = (Pool<Integer, Log>) logs.get(topic);
        if (parts == null) {
            Pool<Integer, Log> found = (Pool<Integer, Log>) logs.putIfNotExists(topic, new Pool<Integer, Log>());
            if (found == null)
                hasNewTopic = true;
            parts = (Pool<Integer, Log>) logs.get(topic);
        }
        Log log = parts.get(partition);
        if (log == null) {
            log = createLog(topic, partition);
            Log found = parts.putIfNotExists(partition, log);
            if (found != null) {
                // there was already somebody there
                log.close();
                log = found;
            } else
                logger.info("Created log for '" + topic + "'-" + partition);
        }
        
        if (hasNewTopic) {
            registerNewTopicInZK(topic);
        }
        return log;
    }
    
    public void registerNewTopicInZK(String topic) {
        if (config.enableZookeeper)
            this.topic = topic;
    }
    
    private Log createLog(String topic, int partition) {
        synchronized (logCreationLock) {
            File d = new File(logDir, topic + "-" + partition);
            d.mkdirs();
            return new Log(d, maxSize, flushInterval, false);
        }
    }
    
    public int chooseRandomPartition(String topic) {
        Integer partition = topicPartitionsMap.get(topic);
        if (partition == null) {
            partition = numPartitions;
        }
        return random.nextInt(partition);
    }
    
    
    Iterator<String> getAllTopics() {
        return logs.keys.iterator();
    }
    
    public void startup() {
        if (config.enableZookeeper) {
            kafkaZookeeper.registerBrokerInZk();
            Iterator<String> iterator = getAllTopics();
            while (iterator.hasNext()) {
                String topic = iterator.next();
                kafkaZookeeper.registerTopicInZk(topic);
            }
            startupLatch.countDown();
        }
        logger.info("Starting log flusher every " + config.flushSchedulerThreadRate + " ms with the following overrides " + logFlushIntervalMap);
        UnitFunction function = new UnitFunction() {
            @Override
            public void call() {
                flushAllLogs();
            }
            
            @Override
            public Object call(Object v) {
                return null;
            }
        };
        
        logFlusherScheduler.scheduleWithRate(function, Long.parseLong(config.flushSchedulerThreadRate + ""), Long.parseLong(config.flushSchedulerThreadRate + ""));
    }
    
    private void flushAllLogs() {
        if (logger.isDebugEnabled())
            logger.debug("flushing the high watermark of all logs");
        Iterator<Log> iterator = getLogIterator();
        while (iterator.hasNext()) {
            Log log = iterator.next();
            try {
                int timeSinceLastFlush = (int) (System.currentTimeMillis() - log.getLastFlushedTime());
                int logFlushInterval = config.defaultFlushIntervalMs;
                if (logFlushIntervalMap.containsKey(log.getTopicName()))
                    logFlushInterval = logFlushIntervalMap.get(log.getTopicName());
                if (logger.isDebugEnabled())
                    logger.debug(log.getTopicName() + " flush interval  " + logFlushInterval +
                            " last flushed " + log.getLastFlushedTime() + " timesincelastFlush: " + timeSinceLastFlush);
                if (timeSinceLastFlush >= logFlushInterval)
                    log.flush();
            } catch (Exception e) {
                logger.error("Error flushing topic " + log.getTopicName(), e);
                logger.fatal("Halting due to unrecoverable I/O error while flushing logs: " + e.getMessage(), e);
                Runtime.getRuntime().halt(1);
            }
        }
    }
    
    
    public void close() {
        logFlusherScheduler.shutdown();
        Iterator<Log> iter = getLogIterator();
        while (iter.hasNext())
            iter.next().close();
        if (config.enableZookeeper) {
            state = "stop";
            kafkaZookeeper.close();
        }
    }
    
}
