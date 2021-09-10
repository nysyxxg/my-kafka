package kafka.consumer;

import clover.org.apache.commons.lang.StringUtils;
import kafka.api.OffsetRequest;
import kafka.cluster.Broker;
import kafka.cluster.Cluster;
import kafka.cluster.Partition;
import kafka.common.InvalidConfigException;
import kafka.utils.Pool;
import kafka.utils.ZKGroupDirs;
import kafka.utils.ZKGroupTopicDirs;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkNodeExistsException;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ZKRebalancerListener implements IZkChildListener {
    private Logger logger = Logger.getLogger(getClass());
    
    String groupId;
    String consumerIdString;
    private ZKGroupDirs dirs;
    private Map<String, List<String>> oldPartitionsPerTopicMap = new HashMap<String, List<String>>();
    private Map<String, List<String>> oldConsumersPerTopicMap = new HashMap<String, List<String>>();
    
    
    private Object rebalanceLock = new Object();
    ConsumerConfig config;
    ZkClient zkClient;
    private Pool<String, Pool<Partition, PartitionTopicInfo>> topicRegistry;
    private Pool<Tuple2<String, String>, BlockingQueue<FetchedDataChunk>> queues;
    private Fetcher fetcher;
    
    public ZKRebalancerListener(ConsumerConfig config, ZkClient zkClient, String groupId, String consumerIdString,
                                Pool<String, Pool<Partition, PartitionTopicInfo>> topicRegistry,
                                Pool<Tuple2<String, String>, BlockingQueue<FetchedDataChunk>> queues,
                                Fetcher fetcher) {
        this.config = config;
        this.zkClient = zkClient;
        this.groupId = groupId;
        this.consumerIdString = consumerIdString;
        this.dirs = new ZKGroupDirs(groupId);
        this.topicRegistry = topicRegistry;
        this.fetcher = fetcher;
        this.queues = queues;
    }
    
    private void releasePartitionOwnership() {
        for (String topic : topicRegistry.keys()) {
            Pool<Partition, PartitionTopicInfo> pool = topicRegistry.get(topic);
            ConcurrentHashMap.KeySetView<Partition, PartitionTopicInfo> keys = pool.keys();
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(groupId, topic);
            for (Partition partition : keys) {
                String znode = topicDirs.getConsumerOwnerDir() + "/" + partition;
                ZkUtils.deletePath(zkClient, znode);
                if (logger.isDebugEnabled())
                    logger.debug("Consumer " + consumerIdString + " releasing " + znode);
            }
        }
    }
    
    
    private Map<String, List<String>> getConsumersPerTopic(String group) {
        List<String> consumers = ZkUtils.getChildrenParentMayNotExist(zkClient, dirs.getConsumerRegistryDir());
        Map<String, List<String>> consumersPerTopicMap = new HashMap<String, List<String>>();
        for (String consumer : consumers) {
            TopicCount topicCount = getTopicCount(consumer);
            Map<String, Set<String>> map = topicCount.getConsumerThreadIdsPerTopic();
            for (String topic : map.keySet()) {
                Set<String> consumerThreadIdSet = map.get(topic);
                List<String> list = consumersPerTopicMap.get(topic);
                for (String consumerThreadId : consumerThreadIdSet) {
                    if (list != null) {
                        list.add(consumerThreadId);
                        consumersPerTopicMap.put(topic, list);
                    } else {
                        list = new ArrayList<>();
                        list.add(consumerThreadId);
                        consumersPerTopicMap.put(topic, list);
                    }
                }
            }
        }
        for (String topic : consumersPerTopicMap.keySet()) {
            List<String> consumerList = consumersPerTopicMap.get(topic);
            Collections.sort(consumerList);
            consumersPerTopicMap.put(topic, consumerList);
        }
        return consumersPerTopicMap;
    }
    
    private Map<String, Set<String>> getRelevantTopicMap(Map<String, Set<String>> myTopicThreadIdsMap,
                                                         Map<String, List<String>> newPartMap,
                                                         Map<String, List<String>> oldPartMap,
                                                         Map<String, List<String>> newConsumerMap,
                                                         Map<String, List<String>> oldConsumerMap) {
        Map<String, Set<String>> relevantTopicThreadIdsMap = new HashMap<String, Set<String>>();
        for (String topic : myTopicThreadIdsMap.keySet()) {
            Set<String> consumerThreadIdSet = myTopicThreadIdsMap.get(topic);
            if (oldPartMap.get(topic) != newPartMap.get(topic) || oldConsumerMap.get(topic) != newConsumerMap.get(topic)) {
                relevantTopicThreadIdsMap.put(topic, consumerThreadIdSet);
            }
        }
        return relevantTopicThreadIdsMap;
    }
    
    private TopicCount getTopicCount(String consumerId) {
        String path = dirs.getConsumerRegistryDir() + "/" + consumerId;
        System.out.println("获取消费者 path : " + path);
        String topicCountJson = ZkUtils.readData(zkClient, path);
        return TopicCount.constructTopicCount(consumerId, topicCountJson);
    }
    
    @Override
    public void handleChildChange(String s, List<String> list) throws Exception {
    }
    
    public void syncedRebalance() {
        System.out.println("-------------------开始执行------------syncedRebalance--------------------------");
        synchronized (rebalanceLock) {
            for (int i = 0; i < ZookeeperConsumerConnector.MAX_N_RETRIES; i++) { // MAX_N_RETRIES 平衡最大重试次数
                logger.info("begin rebalancing consumer " + consumerIdString + " try #" + i); // 开始平衡消费者
                boolean done = false;
                try {
                    done = rebalance();
                } catch (Exception e) {
                    logger.info("exception during rebalance ", e);
                }
                logger.info("end rebalancing consumer " + consumerIdString + " try #" + i);
                if (done) {     // release all partitions, reset state and retry
                    return;
                }
                releasePartitionOwnership();
                resetState();
                try {
                    Thread.sleep(config.zkSyncTimeMs);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        throw new RuntimeException(consumerIdString + " can't rebalance after " + ZookeeperConsumerConnector.MAX_N_RETRIES + " retires");
    }
    
    
    private Boolean rebalance() {
        System.out.println("-------------ZKRebalancerListener------开始执行------------rebalance--------------------------");
        
        Map<String, Set<String>> myTopicThreadIdsMap = getTopicCount(consumerIdString).getConsumerThreadIdsPerTopic();
        Cluster cluster = ZkUtils.getCluster(zkClient);
        Map<String, List<String>> consumersPerTopicMap = getConsumersPerTopic(groupId);
        Iterator<String> topics = myTopicThreadIdsMap.keySet().iterator();
        Map<String, List<String>> partitionsPerTopicMap = ZkUtils.getPartitionsForTopics(zkClient, topics);
        Map<String, Set<String>> relevantTopicThreadIdsMap = getRelevantTopicMap(myTopicThreadIdsMap, partitionsPerTopicMap, oldPartitionsPerTopicMap, consumersPerTopicMap, oldConsumersPerTopicMap);
        if (relevantTopicThreadIdsMap.size() <= 0) {
            logger.info("Consumer " + consumerIdString + " with " + consumersPerTopicMap + " doesn't need to rebalance.");
            return true;
        }
        
        logger.info("Committing all offsets");
        commitOffsets();
        
        logger.info("Releasing partition ownership");
        releasePartitionOwnership();
        
        Set<BlockingQueue<FetchedDataChunk>> queuesToBeCleared = new HashSet<BlockingQueue<FetchedDataChunk>>();
        for (String topic : relevantTopicThreadIdsMap.keySet()) {
            Set<String> consumerThreadIdSet = relevantTopicThreadIdsMap.get(topic);
            topicRegistry.remove(topic);
            topicRegistry.put(topic, new Pool<Partition, PartitionTopicInfo>());
            
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(groupId, topic);
            List<String> curConsumers = consumersPerTopicMap.get(topic);
            List<String> curPartitions = partitionsPerTopicMap.get(topic);
            
            int nPartsPerConsumer = curPartitions.size() / curConsumers.size();
            int nConsumersWithExtraPart = curPartitions.size() % curConsumers.size();
            
            logger.info("Consumer " + consumerIdString + " rebalancing the following partitions: " + curPartitions +
                    " for topic " + topic + " with consumers: " + curConsumers);
            
            for (String consumerThreadId : consumerThreadIdSet) {
                //          val myConsumerPosition = curConsumers.findIndexOf(_ == consumerThreadId)
                //          assert(myConsumerPosition >= 0)
                // xxg
                int myConsumerPosition = curConsumers.indexOf(consumerThreadId);
                assert (myConsumerPosition >= 0);
                
                int startPart = nPartsPerConsumer * myConsumerPosition + Math.min(myConsumerPosition, nConsumersWithExtraPart);
                int nParts = 0;
                if (myConsumerPosition + 1 > nConsumersWithExtraPart) {
                    nParts = nPartsPerConsumer + 0;
                } else {
                    nParts = nPartsPerConsumer + 1;
                }
                
                /**
                 * Range-partition the sorted partitions to consumers for better locality.
                 * The first few consumers pick up an extra partition, if any.
                 */
                if (nParts <= 0) {
                    logger.warn("No broker partitions consumed by consumer thread " + consumerThreadId + " for topic " + topic);
                }else {
                    for (int i = startPart; i < startPart + nParts; i++) {
                        String partition = curPartitions.get(i);
                        logger.info(consumerThreadId + " attempting to claim partition " + partition);
                        if (!processPartition(topicDirs, partition, topic, consumerThreadId)) {
                            return false;
                        }
                    }
                    queuesToBeCleared.add(queues.get(new Tuple2<>(topic, consumerThreadId)));
                }
            }
        }
        updateFetcher(cluster, queuesToBeCleared);
        oldPartitionsPerTopicMap = partitionsPerTopicMap;
        oldConsumersPerTopicMap = consumersPerTopicMap;
        return true;
    }
    
    private void updateFetcher(Cluster cluster, Iterable<BlockingQueue<FetchedDataChunk>> queuesTobeCleared) {
        // update partitions for fetcher
        List<PartitionTopicInfo> allPartitionInfos = new ArrayList<>();
        
        Iterable<Pool<Partition, PartitionTopicInfo>> iterable = topicRegistry.values();
        Iterator<Pool<Partition, PartitionTopicInfo>> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            Pool<Partition, PartitionTopicInfo> partitionInfos = iterator.next();
            Iterator<PartitionTopicInfo> infoIterator = partitionInfos.values().iterator();
            while (infoIterator.hasNext()) {
                PartitionTopicInfo partition = infoIterator.next();
                allPartitionInfos.add(partition);
            }
        }
        Collections.sort(allPartitionInfos, new Comparator<PartitionTopicInfo>() {
            @Override
            public int compare(PartitionTopicInfo o1, PartitionTopicInfo o2) {
                if (o1.partition.equals(o2.partition)) {
                    return 1;
                } else {
                    return -1;
                }
            }
        });
        
        logger.info("Consumer " + consumerIdString + " selected partitions : " + StringUtils.join(allPartitionInfos.toArray(), ","));
        
        if (fetcher != null) {
            fetcher.initConnections(allPartitionInfos, cluster, queuesTobeCleared);
        }
    }
    
    
    private Boolean processPartition(ZKGroupTopicDirs topicDirs, String partition,
                                     String topic, String consumerThreadId) {
        String partitionOwnerPath = topicDirs.getConsumerOwnerDir() + "/" + partition;
        try {
            ZkUtils.createEphemeralPathExpectConflict(zkClient, partitionOwnerPath, consumerThreadId);
        } catch (ZkNodeExistsException e) {
            // The node hasn't been deleted by the original owner. So wait a bit and retry.
            logger.info("waiting for the partition ownership to be deleted: " + partition);
            return false;
        }
        addPartitionTopicInfo(topicDirs, partition, topic, consumerThreadId);
        return true;
    }
    
    
    private void addPartitionTopicInfo(ZKGroupTopicDirs topicDirs, String partitionString,
                                       String topic, String consumerThreadId) {
        Partition partition = Partition.parse(partitionString);
        Pool<Partition, PartitionTopicInfo> partTopicInfoMap = topicRegistry.get(topic);
        
        String znode = topicDirs.getConsumerOffsetDir() + "/" + partition.name;
        String offsetString = ZkUtils.readDataMaybeNull(zkClient, znode);
        // If first time starting a consumer, set the initial offset based on the config
        long offset = 0L;
        if (offsetString == null) {
            String offsetReset = config.autoOffsetReset;
            if (offsetReset == OffsetRequest.SmallestTimeString) {
                offset = earliestOrLatestOffset(topic, partition.getBrokerId(), partition.partId, OffsetRequest.EarliestTime);
            } else if (offsetReset == OffsetRequest.LargestTimeString) {
                offset = earliestOrLatestOffset(topic, partition.getBrokerId(), partition.partId, OffsetRequest.LatestTime);
            } else {
                throw new InvalidConfigException("Wrong value in autoOffsetReset in ConsumerConfig");
            }
        } else {
            offset = Long.valueOf(offsetString);
        }
        BlockingQueue<FetchedDataChunk> queue = queues.get(new Tuple2<>(topic, consumerThreadId));
        AtomicLong consumedOffset = new AtomicLong(offset);
        AtomicLong fetchedOffset = new AtomicLong(offset);
        PartitionTopicInfo partTopicInfo = new PartitionTopicInfo(topic,
                partition.getBrokerId(),
                partition,
                queue,
                consumedOffset,
                fetchedOffset,
                new AtomicInteger(config.fetchSize));
        
        partTopicInfoMap.put(partition, partTopicInfo);
        if (logger.isDebugEnabled())
            logger.debug(partTopicInfo + " selected new offset " + offset);
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
            e.printStackTrace();
            logger.error("error in earliestOrLatestOffset() ", e);
        } finally {
            if (simpleConsumer != null)
                simpleConsumer.close();
        }
        return producedOffset;
    }
    
    private void commitOffsets() {
        if (zkClient == null) {
            return;
        }
        for (String topic : topicRegistry.keys()) {
            Pool<Partition, PartitionTopicInfo> pool = topicRegistry.get(topic);
            Iterable<PartitionTopicInfo> iterable = pool.values();
            Iterator<PartitionTopicInfo> iterator = iterable.iterator();
            ZKGroupTopicDirs topicDirs = new ZKGroupTopicDirs(groupId, topic);
            while (iterator.hasNext()) {
                PartitionTopicInfo info = iterator.next();
                Long newOffset = info.getConsumeOffset();
                try {
                    ZkUtils.updatePersistentPath(zkClient, topicDirs.getConsumerOffsetDir() + "/" + info.partition.name, newOffset.toString());
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
    
    public void resetState() {
        topicRegistry.clear();
        oldConsumersPerTopicMap.clear();
        oldPartitionsPerTopicMap.clear();
    }
}
