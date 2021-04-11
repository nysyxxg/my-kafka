package kafka.consumer;

import kafka.api.OffsetRequest;
import kafka.cluster.Broker;
import kafka.cluster.Cluster;
import kafka.cluster.Partition;
import kafka.serializer.Decoder;
import kafka.serializer.DefaultDecoder;
import kafka.utils.*;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZookeeperConsumerConnector implements ConsumerConnector {
    public static int MAX_N_RETRIES = 4;
    public static FetchedDataChunk shutdownCommand = new FetchedDataChunk(null, null, -1L);
    ConsumerConfig config;
    Boolean enableFetcher;
    
    private Logger logger = Logger.getLogger(getClass());
    private AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private Fetcher fetcher = null;
    private ZkClient zkClient = null;
    private Pool<String, Pool<Partition, PartitionTopicInfo>> topicRegistry = new Pool<String, Pool<Partition, PartitionTopicInfo>>();
    // queues : (topic,consumerThreadId) -> queue
    private Pool<Tuple2<String, String>, BlockingQueue<FetchedDataChunk>> queues = new Pool<Tuple2<String, String>, BlockingQueue<FetchedDataChunk>>();
    
    private KafkaScheduler scheduler = new KafkaScheduler(1, "Kafka-consumer-autocommit-", false);
    
    public ZookeeperConsumerConnector() {
        connectZk();
        createFetcher();
        if (config.autoCommit) {
            logger.info("starting auto committer every " + config.autoCommitIntervalMs + " ms");
            UnitFunction function = new UnitFunction() {
                @Override
                public void call() {
                    autoCommit();
                }
                
                @Override
                public Object call(Object v) {
                    return null;
                }
            };
            scheduler.scheduleWithRate(function, config.autoCommitIntervalMs, config.autoCommitIntervalMs);
        }
    }
    
    private void autoCommit() {
        if (logger.isTraceEnabled()) {
            logger.trace("auto committing");
        }
        try {
            commitOffsets();
        } catch (Throwable t) {
            // log it and let it go
            logger.error("exception during autoCommit: ", t);
        }
    }
    
    private void commitOffsets() {
        if (zkClient == null) {
            return;
        }
        for (String topic : topicRegistry.keys) {
            Pool<Partition, PartitionTopicInfo> pool = topicRegistry.get(topic);
            Iterable<PartitionTopicInfo> iterable = pool.values;
            Iterator<PartitionTopicInfo> iterator = iterable.iterator();
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
            while (iterator.hasNext()) {
                PartitionTopicInfo info = iterator.next();
                Long newOffset = info.getConsumeOffset();
                try {
                    ZkUtils.updatePersistentPath(zkClient, topicDirs.consumerOffsetDir + "/" + info.partition.name, newOffset.toString());
                } catch (Throwable t) {
                    // log it and let it go
                    logger.warn("exception during commitOffsets", t);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Committed offset " + newOffset + " for topic " + info);
                }
            }
        }
    }
    
    
    private void connectZk() {
        logger.info("Connecting to zookeeper instance at " + config.zkConnect);
        zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs, config.zkConnectionTimeoutMs, new ZKStringSerializer());
    }
    
    private void createFetcher() {
        if (enableFetcher) {
            fetcher = new Fetcher(config, zkClient);
        }
    }
    
    public ZookeeperConsumerConnector(ConsumerConfig config) {
        this(config, true);
    }
    
    public ZookeeperConsumerConnector(ConsumerConfig config,
                                      Boolean enableFetcher) {
        this.config = config;
        this.enableFetcher = enableFetcher;
    }
    
    
    public static FetchedDataChunk shutdownCommand() {
        return shutdownCommand;
    }
    
    @Override
    public void shutdown() {
        boolean canShutdown = isShuttingDown.compareAndSet(false, true);
        if (canShutdown) {
            logger.info("ZKConsumerConnector shutting down");
            try {
                scheduler.shutdownNow();
                if (fetcher != null) {
                    fetcher.shutdown();
                }
                sendShudownToAllQueues();
                if (config.autoCommit) {
                    commitOffsets();
                }
                if (zkClient != null) {
                    zkClient.close();
                    zkClient = null;
                }
            } catch (Exception e) {
                logger.fatal("error during consumer connector shutdown", e);
            }
            logger.info("ZKConsumerConnector shut down completed");
        }
        
    }
    
    @Override
    public Map<String, List<KafkaMessageStream<?>>> createMessageStreams(Map<String, Integer> topicCountMap,
                                                                         Decoder<?> decoder) throws UnknownHostException {
        return consume(topicCountMap, new DefaultDecoder());
    }
    
    private Map<String, List<KafkaMessageStream<?>>> consume(Map<String, Integer> topicCountMap,
                                                             DefaultDecoder defaultDecoder) throws UnknownHostException {
        
        logger.debug("entering consume ");
        if (topicCountMap == null)
            throw new RuntimeException("topicCountMap is null");
        
        ZKGroupDirs dirs = new ZKGroupDirs(config.groupId);
        Map<String, List<KafkaMessageStream<?>>> ret = new HashMap<String, List<KafkaMessageStream<?>>>();
        String consumerUuid = null;
        String consumerId = config.getConsumerId();
        
        if (!consumerId.isEmpty()) {
            consumerUuid = consumerId;
        } else {  // generate unique consumerId automatically
            UUID uuid = UUID.randomUUID();
            consumerUuid = "%s-%d-%s".format(
                    InetAddress.getLocalHost().getHostName(), System.currentTimeMillis(),
                    Long.toHexString(uuid.getMostSignificantBits()).substring(0, 8));
        }
        String consumerIdString = config.groupId + "_" + consumerUuid;
        TopicCount topicCount = new TopicCount(consumerIdString, topicCountMap);
        
        // listener to consumer and partition changes
        ZKRebalancerListener loadBalancerListener = new ZKRebalancerListener(config,zkClient,config.groupId, consumerIdString,topicRegistry,queues,fetcher);
        ZkUtils.registerConsumerInZK(zkClient, dirs, consumerIdString, topicCount);
        
        // register listener for session expired event
        zkClient.subscribeStateChanges(new ZKSessionExpireListenner(zkClient,dirs, consumerIdString, topicCount, loadBalancerListener));
        
        zkClient.subscribeChildChanges(dirs.consumerRegistryDir, loadBalancerListener);
        
        // create a queue per topic per consumer thread
        Map<String, Set<String>> consumerThreadIdsPerTopic = topicCount.getConsumerThreadIdsPerTopic();
        for (String topic : consumerThreadIdsPerTopic.keySet()) {
            Set<String> threadIdSet = consumerThreadIdsPerTopic.get(topic);
            List<KafkaMessageStream<?>> streamList = null;
            for (String threadId : threadIdSet) {
                LinkedBlockingQueue stream = new LinkedBlockingQueue<FetchedDataChunk>(config.maxQueuedChunks);
                Tuple2 tuple2 = new Tuple2<>(topic, threadId);
                queues.put(tuple2, stream);
                streamList.add(new KafkaMessageStream(topic, stream, config.consumerTimeoutMs, defaultDecoder));
            }
            ret.put(topic, streamList);
            logger.debug("adding topic " + topic + " and stream to map..");
            
            // register on broker partition path changes
            String partitionPath = ZkUtils.BrokerTopicsPath + "/" + topic;
            ZkUtils.makeSurePersistentPathExists(zkClient, partitionPath);
            zkClient.subscribeChildChanges(partitionPath, loadBalancerListener);
        }
        
        // explicitly trigger load balancing for this consumer
        loadBalancerListener.syncedRebalance();
        return ret;
    }
    
    @Override
    public String getPartOwnerStats() {
        StringBuilder builder = new StringBuilder();
        
        for (String topic : topicRegistry.keys) {
            Pool<Partition, PartitionTopicInfo> pool = topicRegistry.get(topic);
            Iterable<PartitionTopicInfo> iterable = pool.values;
            Iterator<PartitionTopicInfo> iterator = iterable.iterator();
            
            builder.append("\n" + topic + ": [");
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
            
            while (iterator.hasNext()) {
                PartitionTopicInfo partition = iterator.next();
                builder.append("\n    {");
                builder.append(partition.partition.name);
                builder.append(",fetch offset:" + partition.getFetchOffset());
                builder.append(",consumer offset:" + partition.getConsumeOffset());
                builder.append("}");
            }
            builder.append("\n        ]");
        }
        return builder.toString();
    }
    
    @Override
    public String getConsumerGroup() {
        return config.groupId;
    }
    
    @Override
    public Long getOffsetLag(String topic, int brokerId, int partitionId) {
        return getLatestOffset(topic, brokerId, partitionId) - getConsumedOffset(topic, brokerId, partitionId);
    }
    
    @Override
    public Long getConsumedOffset(String topic, int brokerId, int partitionId) {
        Partition partition = new Partition(brokerId, partitionId);
        Pool<Partition, PartitionTopicInfo> partitionInfos = topicRegistry.get(topic);
        if (partitionInfos != null) {
            PartitionTopicInfo partitionInfo = partitionInfos.get(partition);
            if (partitionInfo != null)
                return partitionInfo.getConsumeOffset();
        }
        //otherwise, try to get it from zookeeper
        try {
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
            String znode = topicDirs.consumerOffsetDir + "/" + partition.name;
            String offsetString = ZkUtils.readDataMaybeNull(zkClient, znode);
            if (offsetString != null) {
                return Long.parseLong(offsetString);
            } else {
                return -1L;
            }
        } catch (Exception e) {
            logger.error("error in getConsumedOffset JMX ", e);
        }
        return -2L;
    }
    
    @Override
    public Long getLatestOffset(String topic, int brokerId, int partitionId) {
        return earliestOrLatestOffset(topic, brokerId, partitionId, OffsetRequest.LatestTime);
    }
    
    private Long earliestOrLatestOffset(String topic, int brokerId, int partitionId, Long earliestOrLatest) {
        SimpleConsumer simpleConsumer = null;
        Long producedOffset = -1L;
        try {
            Cluster cluster = ZkUtils.getCluster(zkClient);
            Broker broker = cluster.getBroker(brokerId);
            simpleConsumer = new SimpleConsumer(broker.host, broker.port, ConsumerConfig.SocketTimeout, ConsumerConfig.SocketBufferSize);
            Long[] offsets = simpleConsumer.getOffsetsBefore(topic, partitionId, earliestOrLatest, 1);
            producedOffset = offsets[0];
        } catch (Exception e) {
            logger.error("error in earliestOrLatestOffset() ", e);
        } finally {
            if (simpleConsumer != null)
                simpleConsumer.close();
        }
        return producedOffset;
    }

    
    private void sendShudownToAllQueues() {
        Iterable<BlockingQueue<FetchedDataChunk>> queueValues = queues.values;
        Iterator<BlockingQueue<FetchedDataChunk>> iterator = queueValues.iterator();
        while (iterator.hasNext()) {
            BlockingQueue<FetchedDataChunk> queue = iterator.next();
            logger.debug("Clearing up queue");
            queue.clear();
            try {
                queue.put(ZookeeperConsumerConnector.shutdownCommand);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.debug("Cleared queue and sent shutdown command");
        }
    }
    
    
   
}


