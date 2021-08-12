package kafka.server;

import kafka.message.Message;
import kafka.utils.Utils;
import kafka.utils.ZKConfig;
import scala.Tuple2;

import java.util.Map;
import java.util.Properties;

public class KafkaConfig extends ZKConfig {
    Properties props;
    
    int port;
    
    String hostName;
    
    int brokerId;
    
    int socketSendBuffer;
    
    int socketReceiveBuffer;
    
    int maxSocketRequestSize;
    
    int numThreads;
    
    int monitoringPeriodSecs;
    
    public int numPartitions;
    
    public String logDir;
    
    public int logFileSize;
    
    public int flushInterval;
    
    int logRetentionHours;
    
    public int logRetentionSize;
    
    public Map logRetentionHoursMap;
    
    int logCleanupIntervalMinutes;
    
    public Boolean enableZookeeper;
    
    public Map flushIntervalMap;
    
    public int flushSchedulerThreadRate;
    
    public int defaultFlushIntervalMs;
    
    public Map topicPartitionsMap;
    
    
    public KafkaConfig(Properties props) {
        super(props);
        this.props = props;
        
        this.port = Utils.getInt(props, "port", 6667);
        
        this.hostName = Utils.getString(props, "hostname", null);
        
        this.brokerId = Utils.getInt(props, "brokerid");
        
        this.socketSendBuffer = Utils.getInt(props, "socket.send.buffer", 100 * 1024);
        
        this.socketReceiveBuffer = Utils.getInt(props, "socket.receive.buffer", 100 * 1024);
        
        this.maxSocketRequestSize = Utils.getIntInRange(props, "max.socket.request.bytes", 100 * 1024 * 1024, new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.numThreads = Utils.getIntInRange(props, "num.threads", Runtime.getRuntime().availableProcessors(), new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.monitoringPeriodSecs = Utils.getIntInRange(props, "monitoring.period.secs", 600, new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.numPartitions = Utils.getIntInRange(props, "num.partitions", 1, new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.logDir = Utils.getString(props, "log.dir");
        
        this.logFileSize = Utils.getIntInRange(props, "log.file.size", 1 * 1024 * 1024 * 1024, new Tuple2<>(Message.MinHeaderSize, Integer.MAX_VALUE));
        
        this.flushInterval = Utils.getIntInRange(props, "log.flush.interval", 500, new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.logRetentionHours = Utils.getIntInRange(props, "log.retention.hours", 24 * 7, new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.logRetentionSize = Utils.getInt(props, "log.retention.size", -1);
        
        this.logRetentionHoursMap = Utils.getTopicRentionHours(Utils.getString(props, "topic.log.retention.hours", ""));
        
        this.logCleanupIntervalMinutes = Utils.getIntInRange(props, "log.cleanup.interval.mins", 10, new Tuple2<>(1, Integer.MAX_VALUE));
        
        this.enableZookeeper = Utils.getBoolean(props, "enable.zookeeper", true);
        
        this.flushIntervalMap = Utils.getTopicFlushIntervals(Utils.getString(props, "topic.flush.intervals.ms", ""));
        
        this.flushSchedulerThreadRate = Utils.getInt(props, "log.default.flush.scheduler.interval.ms", 3000);
        
        this.defaultFlushIntervalMs = Utils.getInt(props, "log.default.flush.interval.ms", flushSchedulerThreadRate);
        
        this.topicPartitionsMap = Utils.getTopicPartitions(Utils.getString(props, "topic.partition.count.map", ""));
        
    }
}
