package kafka.consumer;

import kafka.api.OffsetRequest;
import kafka.cluster.Broker;
import kafka.cluster.Cluster;
import kafka.cluster.Partition;
import kafka.serializer.Decoder;
import kafka.utils.*;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher;
import scala.Tuple2;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ZookeeperConsumerConnector extends ConsumerConnector implements ZookeeperConsumerConnectorMBean {
    private Logger logger = Logger.getLogger(getClass());
    
    private ConsumerConfig config;
    private  Boolean enableFetcher;
    
    public static int MAX_N_RETRIES = 4;  // 平衡最大重试次数
    public static FetchedDataChunk shutdownCommand = new FetchedDataChunk(null, null, -1L);
    private AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    private Fetcher fetcher;
    private ZkClient zkClient;
    private Pool<String, Pool<Partition, PartitionTopicInfo>> topicRegistry = new Pool<String, Pool<Partition, PartitionTopicInfo>>();
    // queues : (topic,consumerThreadId) -> queue
    private Pool<Tuple2<String, String>, BlockingQueue<FetchedDataChunk>> queues = new Pool<Tuple2<String, String>, BlockingQueue<FetchedDataChunk>>();
    
    private KafkaScheduler scheduler = new KafkaScheduler(1, "Kafka-consumer-autocommit-", false);
    
    public ZookeeperConsumerConnector(ConsumerConfig config) {
        this(config, true);
    }
    
    public ZookeeperConsumerConnector(ConsumerConfig config,
                                      Boolean enableFetcher) {
        this.config = config;
        this.enableFetcher = enableFetcher;
        initZk();
    }
    
    public void initZk() {
        connectZk();
        createFetcher();
        System.out.println("-------------是否自动提交Offset： " + config.autoCommit);
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
        System.out.println("----------------自动提交offset-----------autoCommit---------");
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
    
    @Override
    public void commitOffsets() {
        if (zkClient == null) {
            return;
        }
        for (String topic : topicRegistry.keys()) {
            Pool<Partition, PartitionTopicInfo> pool = topicRegistry.get(topic);
            Iterable<PartitionTopicInfo> iterable = pool.values();
            Iterator<PartitionTopicInfo> iterator = iterable.iterator();
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
            while (iterator.hasNext()) {
                PartitionTopicInfo info = iterator.next();
                long newOffset = info.getConsumeOffset();
                try {
                    System.out.println("--------------------------保存offset的值：newOffset=  " + newOffset);
                    ZkUtils.updatePersistentPath(zkClient, topicDirs.getConsumerOffsetDir() + "/" + info.partition.name, String.valueOf(newOffset));
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
        logger.info("Connecting to zookeeper instance at " + config.getZkConnect());
        zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs, config.zkConnectionTimeoutMs, new ZKStringSerializer());
    }
    
    private void createFetcher() {
        System.out.println("------------是否创建 createFetcher ： enableFetcher ：" + enableFetcher);
        if (enableFetcher) {
            fetcher = new Fetcher(config, zkClient);
        }
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
    public <T> Map<String, List<KafkaMessageStream<T>>> createMessageStreams(Map<String, Integer> topicCountMap,
                                                                             Decoder<T> decoder) throws UnknownHostException {
        return consume(topicCountMap, decoder);
    }
    
    public <T> Map<String, List<KafkaMessageStream<T>>> consume(Map<String, Integer> topicCountMap,
                                                                Decoder<T> defaultDecoder) throws UnknownHostException {
        
        logger.debug("entering consume ");
        if (topicCountMap == null) {
            throw new RuntimeException("topicCountMap is null");
        }
        
        ZKGroupDirs dirs = new ZKGroupDirs(config.groupId);
        Map<String, List<KafkaMessageStream<T>>> ret = new HashMap<String, List<KafkaMessageStream<T>>>();
        String consumerUuid = null;
        String consumerId = config.getConsumerId();
        
        if (consumerId != null) {
            consumerUuid = consumerId;
        } else {  // generate unique consumerId automatically
            UUID uuid = UUID.randomUUID();
            // "%s-%d-%s".format();
            consumerUuid = String.format( "%s-%d-%s",
                    InetAddress.getLocalHost().getHostName(), System.currentTimeMillis(),
                    Long.toHexString(uuid.getMostSignificantBits()).substring(0, 8));
        }
        String consumerIdString = config.groupId + "_" + consumerUuid;
        TopicCount topicCount = new TopicCount(consumerIdString, topicCountMap);
        
        // listener to consumer and partition changes
        // 监听 消费者 和 topic的分区变化，可能在同一个消费组中， 增加了新的消费者，或者订阅的topic的分区发生了改变
        ZKRebalancerListener loadBalancerListener = new ZKRebalancerListener(config, zkClient, config.groupId, consumerIdString, topicRegistry, queues, fetcher);
        ZkUtils.registerConsumerInZK(zkClient, dirs, consumerIdString, topicCount);
        
        // register listener for session expired event   订阅节点的状态变化
        // 订阅节点连接及状态的变化情况
        zkClient.subscribeStateChanges(new ZKSessionExpireListenner(dirs, consumerIdString, topicCount, loadBalancerListener));
        // 订阅 孩子节点数据的变化
        zkClient.subscribeChildChanges(dirs.getConsumerRegistryDir(), loadBalancerListener);
        
        // create a queue per topic per consumer thread
        Map<String, Set<String>> consumerThreadIdsPerTopic = topicCount.getConsumerThreadIdsPerTopic();
        for (String topic : consumerThreadIdsPerTopic.keySet()) {
            Set<String> threadIdSet = consumerThreadIdsPerTopic.get(topic);
            List<KafkaMessageStream<T>> streamList = new ArrayList<>();
            for (String threadId : threadIdSet) {
                LinkedBlockingQueue<FetchedDataChunk> stream = new LinkedBlockingQueue<FetchedDataChunk>(config.maxQueuedChunks);
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
        
        for (String topic : topicRegistry.keys()) {
            Pool<Partition, PartitionTopicInfo> pool = topicRegistry.get(topic);
            Iterable<PartitionTopicInfo> iterable = pool.values();
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
            if (partitionInfo != null) {
                return partitionInfo.getConsumeOffset();
            }
        }
        //otherwise, try to get it from zookeeper
        try {
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(config.groupId, topic);
            String znode = topicDirs.getConsumerOffsetDir() + "/" + partition.name;
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
    
    private Long earliestOrLatestOffset(String topic, int brokerId, int partitionId, long earliestOrLatest) {
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
            if (simpleConsumer != null) {
                simpleConsumer.close();
            }
        }
        return producedOffset;
    }
    
    
    private void sendShudownToAllQueues() {
        Iterable<BlockingQueue<FetchedDataChunk>> queueValues = queues.values();
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
    
    
    class ZKSessionExpireListenner implements IZkStateListener {
        private Logger logger = Logger.getLogger(ZKSessionExpireListenner.class);
        ZKGroupDirs dirs;
        String consumerIdString;
        TopicCount topicCount;
        ZKRebalancerListener loadBalancerListener;
        
        public ZKSessionExpireListenner(ZKGroupDirs dirs, String consumerIdString,
                                        TopicCount topicCount, ZKRebalancerListener loadBalancerListener) {
            this.dirs = dirs;
            this.consumerIdString = consumerIdString;
            this.loadBalancerListener = loadBalancerListener;
            this.topicCount = topicCount;
        }
        
        @Override
        public void handleStateChanged(Watcher.Event.KeeperState keeperState) throws Exception {
            System.out.println("节点连接及状态变化："+keeperState.name());
        }
        
        @Override
        public void handleNewSession() throws Exception {
            logger.info("ZK expired; release old broker parition ownership; re-register consumer " + consumerIdString);
            System.out.println("节点Session变化。。。");
            loadBalancerListener.resetState();
            ZkUtils.registerConsumerInZK(zkClient, dirs, consumerIdString, topicCount);
            // explicitly trigger load balancing for this consumer
            loadBalancerListener.syncedRebalance();
        }
    }
 
}


