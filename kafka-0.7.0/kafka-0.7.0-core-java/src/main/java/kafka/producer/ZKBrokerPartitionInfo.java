package kafka.producer;

import kafka.cluster.Broker;
import kafka.cluster.Partition;
import kafka.utils.ZKConfig;
import kafka.utils.ZKStringSerializer;
import kafka.utils.ZkUtils;
import org.I0Itec.zkclient.IZkChildListener;
import org.I0Itec.zkclient.IZkStateListener;
import org.I0Itec.zkclient.ZkClient;
import org.apache.log4j.Logger;
import org.apache.zookeeper.Watcher;
import scala.Tuple2;

import java.util.*;

public class ZKBrokerPartitionInfo implements BrokerPartitionInfo {
    private Logger logger = Logger.getLogger(ZKBrokerPartitionInfo.class);
    private Object zkWatcherLock = new Object();
    
    ZKConfig config;
    ProducerPool producerPool;
    Boolean populateProducerPool;
    private ZkClient zkClient;
    Map<String, SortedSet<Partition>> topicBrokerPartitions;
    Map<Integer, Broker> allBrokers;
    BrokerTopicsListener brokerTopicsListener;
    
    public ZKBrokerPartitionInfo(ZKConfig config, ProducerPool producerPool,
                                 Boolean populateProducerPool) {
        this.config = config;
        this.producerPool = producerPool;
        this.populateProducerPool = populateProducerPool;
        this.zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs, config.zkConnectionTimeoutMs,
                new ZKStringSerializer());
        // maintain a map from topic -> list of (broker, num_partitions) from zookeeper
        this.topicBrokerPartitions = getZKTopicPartitionInfo();
        // maintain a map from broker id to the corresponding Broker object
        this.allBrokers = getZKBrokerInfo();
        
        // use just the brokerTopicsListener for all watchers
        this.brokerTopicsListener = new BrokerTopicsListener(topicBrokerPartitions, allBrokers);
        // register listener for change of topics to keep topicsBrokerPartitions updated
        zkClient.subscribeChildChanges(ZkUtils.BrokerTopicsPath, brokerTopicsListener);
        
        // register listener for change of brokers for each topic to keep topicsBrokerPartitions updated
        for (String topic : topicBrokerPartitions.keySet()) {
            zkClient.subscribeChildChanges(ZkUtils.BrokerTopicsPath + "/" + topic, brokerTopicsListener);
            logger.debug("Registering listener on path: " + ZkUtils.BrokerTopicsPath + "/" + topic);
        }
        
        // register listener for new broker
        zkClient.subscribeChildChanges(ZkUtils.BrokerIdsPath, brokerTopicsListener);
        // register listener for session expired event
        zkClient.subscribeStateChanges(new ZKSessionExpirationListener(brokerTopicsListener));
        
    }
    
    private Map<Integer, Broker> getZKBrokerInfo() {
        Map<Integer, Broker> brokers = new HashMap<Integer, Broker>();
        List<String> stringList = ZkUtils.getChildrenParentMayNotExist(zkClient, ZkUtils.BrokerIdsPath);
        
        List<Integer> allBrokerIds = new ArrayList<>();
        for (String bid : stringList) {
            allBrokerIds.add(Integer.parseInt(bid));
            String brokerInfo = ZkUtils.readData(zkClient, ZkUtils.BrokerIdsPath + "/" + bid);
            brokers.put(Integer.parseInt(bid), Broker.createBroker(Integer.parseInt(bid), brokerInfo));
        }
        return brokers;
    }
    
    private Map<String, SortedSet<Partition>> getZKTopicPartitionInfo() {
        Map<String, SortedSet<Partition>> brokerPartitionsPerTopic = new HashMap<String, SortedSet<Partition>>();
        
        ZkUtils.makeSurePersistentPathExists(zkClient, ZkUtils.BrokerTopicsPath);
        List<String> topics = ZkUtils.getChildrenParentMayNotExist(zkClient, ZkUtils.BrokerTopicsPath);
        for (String topic : topics) {
            // find the number of broker partitions registered for this topic
            String brokerTopicPath = ZkUtils.BrokerTopicsPath + "/" + topic;
            List<String> brokerList = ZkUtils.getChildrenParentMayNotExist(zkClient, brokerTopicPath);
            
            List<Tuple2<Integer, Integer>> brokerPartitions = new ArrayList<>();
            for (String bid : brokerList) {
                String data = ZkUtils.readData(zkClient, brokerTopicPath + "/" + bid);
                Tuple2<Integer, Integer> tuple2 = new Tuple2<Integer, Integer>(Integer.parseInt(bid), Integer.parseInt(data));
                brokerPartitions.add(tuple2);
            }
            
            Collections.sort(brokerPartitions, new Comparator<Tuple2<Integer, Integer>>() {
                @Override
                public int compare(Tuple2<Integer, Integer> o1, Tuple2<Integer, Integer> o2) {
                    return o2._1 - o2._2();
                }
            });
            
            // val sortedBrokerPartitions = brokerPartitions.sortWith((id1, id2) => id1._1 < id2._1)
            if (logger.isDebugEnabled()) {
                logger.debug("Broker ids and # of partitions on each for topic: " + topic + " = " + brokerPartitions.toString());
            }
            SortedSet<Partition> brokerParts = new TreeSet<Partition>();
            for (Tuple2<Integer, Integer> bp : brokerPartitions) {
                for (int i = 0; i < bp._2; i++) {
                    Partition bidPid = new Partition(bp._1, i);
                    brokerParts.add(bidPid);
                }
            }
            brokerPartitionsPerTopic.put(topic, brokerParts);
            if (logger.isDebugEnabled())
                logger.debug("Sorted list of broker ids and partition ids on each for topic: " + topic + " = " + brokerParts.toString());
        }
        return brokerPartitionsPerTopic;
    }
    
    
    private SortedSet<Partition> getBrokerPartitions(ZkClient zkClient, String topic, List<Integer> brokerList) {
        String brokerTopicPath = ZkUtils.BrokerTopicsPath + "/" + topic;
        
        List<Tuple2<Integer, Integer>> brokerPartitions = new ArrayList<>();
        for (Integer bid : brokerList) {
            Integer data = Integer.valueOf(ZkUtils.readData(zkClient, brokerTopicPath + "/" + bid));
            Tuple2<Integer, Integer> tuple2 = new Tuple2<Integer, Integer>(bid, data);
            brokerPartitions.add(tuple2);
        }
        
        Collections.sort(brokerPartitions, new Comparator<Tuple2<Integer, Integer>>() {
            @Override
            public int compare(Tuple2<Integer, Integer> o1, Tuple2<Integer, Integer> o2) {
                return o2._1 - o2._2();
            }
        });
        
        SortedSet<Partition> brokerParts = new TreeSet();
        for (Tuple2<Integer, Integer> bp : brokerPartitions) {
            for (int i = 0; i < bp._2; i++) {
                Partition bidPid = new Partition(bp._1, i);
                brokerParts.add(bidPid);
            }
        }
        return brokerParts;
    }
    
    
    @Override
    public SortedSet<Partition> getBrokerPartitionInfo(String topic) {
        synchronized (zkWatcherLock) {
            SortedSet<Partition> brokerPartitions = topicBrokerPartitions.get(topic);
            SortedSet<Partition> numBrokerPartitions = new TreeSet<>();
            if (brokerPartitions != null) {
                if (brokerPartitions.size() == 0) {
                    // no brokers currently registered for this topic. Find the list of all brokers in the cluster.
                    numBrokerPartitions = bootstrapWithExistingBrokers(topic);
                    topicBrokerPartitions.put(topic, numBrokerPartitions);
                } else {
                    numBrokerPartitions.addAll(brokerPartitions);
                }
            } else {
                // no brokers currently registered for this topic. Find the list of all brokers in the cluster.
                numBrokerPartitions = bootstrapWithExistingBrokers(topic);
                topicBrokerPartitions.put(topic, numBrokerPartitions);
            }
            return numBrokerPartitions;
        }
    }
    
    private SortedSet<Partition> bootstrapWithExistingBrokers(String topic) {
        if (logger.isDebugEnabled()) logger.debug("Currently, no brokers are registered under topic: " + topic);
        if (logger.isDebugEnabled()) {
            logger.debug("Bootstrapping topic: " + topic + " with available brokers in the cluster with default " +
                    "number of partitions = 1");
        }
        List<String> allBrokersIds = ZkUtils.getChildrenParentMayNotExist(zkClient, ZkUtils.BrokerIdsPath);
        if (logger.isTraceEnabled())
            logger.trace("List of all brokers currently registered in zookeeper = " + allBrokersIds.toString());
        // since we do not have the in formation about number of partitions on these brokers, just assume single partition
        // i.e. pick partition 0 from each broker as a candidate
        SortedSet<Partition> numBrokerPartitions = new TreeSet<Partition>();
        allBrokersIds.forEach(b -> numBrokerPartitions.add(new Partition(Integer.parseInt(b), 0)));
        // add the rest of the available brokers with default 1 partition for this topic, so all of the brokers
        // participate in hosting this topic.
        if (logger.isDebugEnabled()) {
            logger.debug("Adding following broker id, partition id for NEW topic: " + topic + "=" + numBrokerPartitions.toString());
        }
        return numBrokerPartitions;
    }
    
    
    @Override
    public Broker getBrokerInfo(int brokerId) {
        synchronized (zkWatcherLock) {
            return allBrokers.get(brokerId);
        }
    }
    
    @Override
    public Map<Integer, Broker> getAllBrokerInfo() {
        return allBrokers;
    }
    
    @Override
    public void updateInfo() {
        synchronized (zkWatcherLock) {
            topicBrokerPartitions = getZKTopicPartitionInfo();
            allBrokers = getZKBrokerInfo();
        }
    }
    
    @Override
    public void close() {
        zkClient.close();
    }
    
    class BrokerTopicsListener implements IZkChildListener {
        private Logger logger = Logger.getLogger(BrokerTopicsListener.class);
        Map<String, SortedSet<Partition>> originalBrokerTopicsPartitionsMap;
        Map<Integer, Broker> originalBrokerIdMap;
        
        private Map<String, SortedSet<Partition>> oldBrokerTopicPartitionsMap = originalBrokerTopicsPartitionsMap;
        private Map<Integer, Broker> oldBrokerIdMap = originalBrokerIdMap;
        
        BrokerTopicsListener(Map<String, SortedSet<Partition>> originalBrokerTopicsPartitionsMap,
                             Map<Integer, Broker> originalBrokerIdMap) {
            this.originalBrokerTopicsPartitionsMap = originalBrokerTopicsPartitionsMap;
            this.originalBrokerIdMap = originalBrokerIdMap;
            
            this.oldBrokerTopicPartitionsMap = this.originalBrokerTopicsPartitionsMap;
            this.oldBrokerIdMap = this.originalBrokerIdMap;
            
            if (logger.isDebugEnabled())
                logger.debug("[BrokerTopicsListener] Creating broker topics listener to watch the following paths - \n" +
                        "/broker/topics, /broker/topics/topic, /broker/ids");
            if (logger.isDebugEnabled())
                logger.debug("[BrokerTopicsListener] Initialized this broker topics listener with initial mapping of broker id to " +
                        "partition id per topic with " + oldBrokerTopicPartitionsMap.toString());
            
        }
        
        
        @Override
        public void handleChildChange(String parentPath, List<String> currentChildren) throws Exception {
            List<String> curChilds = null;
            if (currentChildren != null) {
                curChilds = currentChildren;
            } else {
                new java.util.ArrayList<String>();
            }
            synchronized (zkWatcherLock) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Watcher fired for path: " + parentPath + " with change " + curChilds.toString());
                }
                if (parentPath.equals("/brokers/topics")) {        // this is a watcher for /broker/topics path
                    List<String> updatedTopics = curChilds;
                    if (logger.isDebugEnabled()) {
                        logger.debug("[BrokerTopicsListener] List of topics changed at " + parentPath + " Updated topics -> " +
                                curChilds.toString());
                        logger.debug("[BrokerTopicsListener] Old list of topics: " + oldBrokerTopicPartitionsMap.keySet().toString());
                        logger.debug("[BrokerTopicsListener] Updated list of topics: " + updatedTopics.toString());
                    }
                    // val newTopics = updatedTopics & ~ oldBrokerTopicPartitionsMap.keySet();
                    List<String> newTopics = new ArrayList<>();
                    if (logger.isDebugEnabled()) {
                        logger.debug("[BrokerTopicsListener] List of newly registered topics: " + newTopics.toString());
                    }
                    for (String topic : newTopics) {
                        String brokerTopicPath = ZkUtils.BrokerTopicsPath + "/" + topic;
                        List<String> brokerList = ZkUtils.getChildrenParentMayNotExist(zkClient, brokerTopicPath);
                        processNewBrokerInExistingTopic(topic, brokerList);
                        zkClient.subscribeChildChanges(ZkUtils.BrokerTopicsPath + "/" + topic, brokerTopicsListener);
                    }
                } else if (parentPath.equals("/brokers/ids")) {        // this is a watcher for /broker/ids path
                    if (logger.isDebugEnabled())
                        logger.debug("[BrokerTopicsListener] List of brokers changed in the Kafka cluster " + parentPath +
                                "\t Currently registered list of brokers -> " + curChilds.toString());
                    processBrokerChange(parentPath, curChilds);
                } else {
                    String[] pathSplits = parentPath.split("/");
                    String topic = pathSplits[pathSplits.length - 1];
                    if (pathSplits.length == 4 && pathSplits[2].equals("topics")) {
                        if (logger.isDebugEnabled())
                            logger.debug("[BrokerTopicsListener] List of brokers changed at " + parentPath + "\t Currently registered " +
                                    " list of brokers -> " + curChilds.toString() + " for topic -> " + topic);
                        processNewBrokerInExistingTopic(topic, curChilds);
                    }
                }
                
                // update the data structures tracking older state values
                oldBrokerTopicPartitionsMap = topicBrokerPartitions;
                oldBrokerIdMap = allBrokers;
            }
        }
        
        void processBrokerChange(String parentPath, List<String> curChilds) {
            if (parentPath.equals(ZkUtils.BrokerIdsPath)) {
                Set<Integer> updatedBrokerList = new HashSet<>();
                // asScalaBuffer().map(bid = > bid.toInt)
                for (String bid : curChilds) {
                    updatedBrokerList.add(Integer.parseInt(bid));
                }
                Set<Integer> idsList = oldBrokerIdMap.keySet();
                Set<Integer> newBrokers = new HashSet<Integer>();
                // 计算两个集合的差集
                newBrokers.addAll(updatedBrokerList);
                newBrokers.removeAll(idsList);
                
                
                if (logger.isDebugEnabled()) {
                    logger.debug("[BrokerTopicsListener] List of newly registered brokers: " + newBrokers.toString());
                }
                for (Integer bid : newBrokers) {
                    String brokerInfo = ZkUtils.readData(zkClient, ZkUtils.BrokerIdsPath + "/" + bid);
                    String brokerHostPort[] = brokerInfo.split(":");
                    allBrokers.put(bid, new Broker(bid, brokerHostPort[1], brokerHostPort[1], Integer.parseInt(brokerHostPort[2])));
                    if (logger.isDebugEnabled())
                        logger.debug("[BrokerTopicsListener] Invoking the callback for broker: " + bid);
                    producerCbk(producerPool, populateProducerPool, bid, brokerHostPort[1], Integer.parseInt(brokerHostPort[2]));
                }
                // remove dead brokers from the in memory list of live brokers
                Set<Integer> deadBrokers = new HashSet<Integer>();
                deadBrokers.addAll(oldBrokerIdMap.keySet());
                deadBrokers.removeAll(updatedBrokerList);
                if (logger.isDebugEnabled())
                    logger.debug("[BrokerTopicsListener] Deleting broker ids for dead brokers: " + deadBrokers.toString());
                for (Integer bid : deadBrokers) {
                    allBrokers.remove(bid);
                    // also remove this dead broker from particular topics
                    for (String topic : topicBrokerPartitions.keySet()) {
                        SortedSet<Partition> sortedSet = topicBrokerPartitions.get(topic);
                        if (sortedSet != null) {
                            SortedSet<Partition> aliveBrokerPartitionList = new TreeSet<>();
                            for (Partition bp : aliveBrokerPartitionList) {
                                if (bp.getBrokerId() == bid) {
                                    aliveBrokerPartitionList.add(bp);
                                }
                            }
                            topicBrokerPartitions.put(topic, aliveBrokerPartitionList);
                            if (logger.isDebugEnabled())
                                logger.debug("[BrokerTopicsListener] Removing dead broker ids for topic: " + topic + "\t " +
                                        "Updated list of broker id, partition id = " + aliveBrokerPartitionList.toString());
                        }
                    }
                }
            }
        }
        
        private void producerCbk(ProducerPool producerPool, boolean populateProducerPool, int bid, String host, int port) {
            if (populateProducerPool) {
                producerPool.addProducer(new Broker(bid, host, host, port));
            } else logger.debug("Skipping the callback since populateProducerPool = false");
        }
        
        void processNewBrokerInExistingTopic(String topic, List<String> curChilds) {
            // find the old list of brokers for this topic
            SortedSet<Partition> sortedSet = oldBrokerTopicPartitionsMap.get(topic);
            if (sortedSet != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("[BrokerTopicsListener] Old list of brokers: " + sortedSet.toString());
                }
            }
            List<Integer> updatedBrokerList = new ArrayList<>();
            curChilds.forEach(b -> updatedBrokerList.add(Integer.valueOf(b)));
            
            SortedSet<Partition> updatedBrokerParts = getBrokerPartitions(zkClient, topic, updatedBrokerList);
            
            if (logger.isDebugEnabled())
                logger.debug("[BrokerTopicsListener] Currently registered list of brokers for topic: " + topic + " are " +
                        curChilds.toString());
            // update the number of partitions on existing brokers
            SortedSet<Partition> mergedBrokerParts = updatedBrokerParts;
            SortedSet<Partition> oldBrokerParts = topicBrokerPartitions.get(topic);
            if (oldBrokerParts != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("[BrokerTopicsListener] Unregistered list of brokers for topic: " + topic + " are " + oldBrokerParts.toString());
                }
                oldBrokerParts.addAll(updatedBrokerParts);
                mergedBrokerParts = oldBrokerParts;
            }
            // keep only brokers that are alive
            SortedSet<Partition> mergedBrokerParts2 = new TreeSet<>();
            for (Partition bp : mergedBrokerParts) {
                if (allBrokers.containsKey(bp.getBrokerId())) {
                    mergedBrokerParts2.add(bp);
                }
            }
            topicBrokerPartitions.put(topic, mergedBrokerParts2);
            
            if (logger.isDebugEnabled()) {
                logger.debug("[BrokerTopicsListener] List of broker partitions for topic: " + topic + " are " + mergedBrokerParts.toString());
            }
        }
        
        void resetState() {
            if (logger.isTraceEnabled())
                logger.trace("[BrokerTopicsListener] Before reseting broker topic partitions state " +
                        oldBrokerTopicPartitionsMap.toString());
            oldBrokerTopicPartitionsMap = topicBrokerPartitions;
            if (logger.isDebugEnabled())
                logger.debug("[BrokerTopicsListener] After reseting broker topic partitions state " +
                        oldBrokerTopicPartitionsMap.toString());
            if (logger.isTraceEnabled())
                logger.trace("[BrokerTopicsListener] Before reseting broker id map state " + oldBrokerIdMap.toString());
            oldBrokerIdMap = allBrokers;
            if (logger.isDebugEnabled())
                logger.debug("[BrokerTopicsListener] After reseting broker id map state " + oldBrokerIdMap.toString());
        }
        
    }
    
    class ZKSessionExpirationListener implements IZkStateListener {
        ZKSessionExpirationListener(BrokerTopicsListener brokerTopicsListener) {
        
        }
        
        public void handleStateChanged(Watcher.Event.KeeperState state) {
            // do nothing, since zkclient will do reconnect for us.
        }
        
        
        public void handleNewSession() {
            /**
             *  When we get a SessionExpired event, we lost all ephemeral nodes and zkclient has reestablished a
             *  connection for us.
             */
            logger.info("ZK expired; release old list of broker partitions for topics ");
            topicBrokerPartitions = getZKTopicPartitionInfo();
            allBrokers = getZKBrokerInfo();
            brokerTopicsListener.resetState();
            
            // register listener for change of brokers for each topic to keep topicsBrokerPartitions updated
            // NOTE: this is probably not required here. Since when we read from getZKTopicPartitionInfo() above,
            // it automatically recreates the watchers there itself
            for (String topic : topicBrokerPartitions.keySet()) {
                zkClient.subscribeChildChanges(ZkUtils.BrokerTopicsPath + "/" + topic, brokerTopicsListener);
            }
            // there is no need to re-register other listeners as they are listening on the child changes of
            // permanent nodes
        }
        
    }
}
