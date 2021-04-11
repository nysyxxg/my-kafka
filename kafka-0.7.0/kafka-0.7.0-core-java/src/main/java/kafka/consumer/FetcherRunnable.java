package kafka.consumer;

import kafka.api.OffsetRequest;
import kafka.cluster.Broker;
import kafka.cluster.Partition;
import kafka.utils.ZKGroupTopicDirs;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class FetcherRunnable extends Thread {
    String name;
    ZkClient zkClient;
    ConsumerConfig config;
    Broker broker;
    List<PartitionTopicInfo> partitionTopicInfos;
    
    
    private Logger logger = Logger.getLogger(getClass());
    private CountDownLatch shutdownLatch = new CountDownLatch(1);
    private SimpleConsumer simpleConsumer;
    private volatile boolean stopped = false;
    
    
    public FetcherRunnable(String threadName,
                           ZkClient zkClient,
                           ConsumerConfig config,
                           Broker broker,
                           List<PartitionTopicInfo> partitionTopicInfos) {
        this.name = name;
        this.zkClient = zkClient;
        this.config = config;
        this.broker = broker;
        this.partitionTopicInfos = partitionTopicInfos;
        this.simpleConsumer = new SimpleConsumer(this.broker.host, this.broker.port, config.socketTimeoutMs, config.socketBufferSize);
    }
    
    
    public FetcherRunnable(ZkClient zkClient, ConsumerConfig config,
                           Broker broker, List<PartitionTopicInfo> partitionTopicInfos) {
        this(null, zkClient, config, broker, partitionTopicInfos);
        
    }
    
    public void shutdown() {
        stopped = true;
        interrupt();
        logger.debug("awaiting shutdown on fetcher " + name);
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.debug("shutdown of fetcher " + name + " thread complete");
    }
    
    public void shutdownComplete() {
        shutdownLatch.countDown();
    }
    
    public Long resetConsumerOffsets(String topic, Partition partition) {
        Long offset = 0L;
        String autoOffsetReset = config.autoOffsetReset;
        if (OffsetRequest.SmallestTimeString.equalsIgnoreCase(autoOffsetReset)) {
            offset = OffsetRequest.EarliestTime;
        } else if (OffsetRequest.LargestTimeString.equalsIgnoreCase(OffsetRequest.LargestTimeString)) {
            offset = OffsetRequest.LatestTime;
        } else {
            return -1l;
        }
      
        Long offsets[] = simpleConsumer.getOffsetsBefore(topic, partition.partId, offset, 1);
        ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
        
        String signStr = "";
        if (offset == OffsetRequest.EarliestTime) {
            signStr = "earliest ";
        } else {
            signStr = " latest ";
        }
        
        // reset manually in zookeeper
        logger.info("updating partition " + partition.name + " for topic " + topic + " with " + signStr + "offset " + offsets[0]);
        
        ZkUtils.updatePersistentPath(zkClient, topicDirs.consumerOffsetDir + "/" + partition.name, String.valueOf(offsets[0]));
        
        return offsets[0];
    }
    
    
}
