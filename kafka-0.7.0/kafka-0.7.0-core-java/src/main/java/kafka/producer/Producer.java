package kafka.producer;

import kafka.cluster.Broker;
import kafka.cluster.Partition;
import kafka.common.InvalidConfigException;
import kafka.common.InvalidPartitionException;
import kafka.common.NoBrokersForPartitionException;
import kafka.serializer.Encoder;
import kafka.utils.Utils;
import kafka.utils.ZKConfig;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;


import kafka.producer.async.CallbackHandler;
import kafka.producer.async.EventHandler;

import kafka.api.ProducerRequest;

public class Producer<K, V> {
    
    private Logger logger = Logger.getLogger(Producer.class);
    private AtomicBoolean hasShutdown = new AtomicBoolean(false);
    
    ProducerConfig config;
    Partitioner<K> partitioner;
    ProducerPool<V> producerPool;
    Boolean populateProducerPool;
    BrokerPartitionInfo brokerPartitionInfo;
    
    private Random random = new java.util.Random();
    
    private Boolean zkEnabled;
    
    public Producer(ProducerConfig producerConfig) {
        this.config = producerConfig;
    }
    
    public Producer(ProducerConfig producerConfig,
                    Partitioner<K> partitioner,
                    ProducerPool<V> producerPool,
                    Boolean populateProducerPool,
                    BrokerPartitionInfo brokerPartitionInfo) {
        this.config = producerConfig;
        this.partitioner = partitioner;
        this.producerPool = producerPool;
        this.populateProducerPool = populateProducerPool;
        this.brokerPartitionInfo = brokerPartitionInfo;
        initCheck();
    }
    
    private void initCheck() {
        if (!Utils.propertyExists(config.zkConnect) && !Utils.propertyExists(config.brokerList)) {
            throw new InvalidConfigException("At least one of zk.connect or broker.list must be specified");
        }
        if (Utils.propertyExists(config.zkConnect) && Utils.propertyExists(config.brokerList)) {
            logger.warn("Both zk.connect and broker.list provided (zk.connect takes precedence).");
        }
        this.zkEnabled = Utils.propertyExists(config.zkConnect);
        
        if (brokerPartitionInfo == null) {
            if (zkEnabled) {
                Properties zkProps = new Properties();// 封装zk的连接信息
                zkProps.put("zk.connect", config.zkConnect);
                zkProps.put("zk.sessiontimeout.ms", config.zkSessionTimeoutMs);
                zkProps.put("zk.connectiontimeout.ms", config.zkConnectionTimeoutMs);
                zkProps.put("zk.synctime.ms", config.zkSyncTimeMs);
                brokerPartitionInfo = new ZKBrokerPartitionInfo(new ZKConfig(zkProps), producerPool, populateProducerPool); // 实例化broker相关信息的实例对象
            } else {
                brokerPartitionInfo = new ConfigBrokerPartitionInfo(config);
            }
        }
        // pool of producers, one per broker
        if (populateProducerPool) {
            Map<Integer, Broker> allBrokers = brokerPartitionInfo.getAllBrokerInfo();
            // 遍历每一个broker对象，为每一个broker创建一个Producer对象
            for (Integer key : allBrokers.keySet()) {
                Broker broker = allBrokers.get(key);
                producerPool.addProducer(new Broker(key, broker.host, broker.host, broker.port));
            }
        }
    }
    
    public void producerCbk(int bid, String host, int port) {
        if (populateProducerPool) {
            producerPool.addProducer(new Broker(bid, host, host, port));
        } else {
            logger.debug("Skipping the callback since populateProducerPool = false");
        }
    }
    
    public Producer(ProducerConfig config, Partitioner partitioner, Encoder encoder)
            throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException {
//        Partitioner partitioner = (Partitioner) Utils.getObject(config.partitionerClass);
//        Encoder encoder =  (Encoder)Utils.getObject(config.serializerClass);
        this(config, partitioner, new ProducerPool<V>(config, encoder), true, null);
    }
    
    
    public Producer(ProducerConfig config,
                    Encoder<V> encoder,
                    EventHandler<V> eventHandler,
                    CallbackHandler<V> cbkHandler,
                    Partitioner<K> partitioner) {

//        if (partitioner == null) {
//            partitioner = new DefaultPartitioner<K>();
//        }
        
        this(config, partitioner,
                new ProducerPool<V>(config, encoder, eventHandler, cbkHandler), true, null);
    }
    
    private int getPartition(K key, int numPartitions) {
        if (numPartitions <= 0) {
            throw new InvalidPartitionException("Invalid number of partitions: " + numPartitions + "\n Valid values are > 0");
        }
        int partition = 0;
        if (key == null) {
            partition = random.nextInt(numPartitions);
        } else {
            partition = partitioner.partition(key, numPartitions);
        }
        if (partition < 0 || partition >= numPartitions)
            throw new InvalidPartitionException("Invalid partition id : " + partition + "\n Valid values are in the range inclusive [0, " + (numPartitions - 1) + "]");
        return partition;
    }
    
    public void send(ProducerData<K, V>... producerData) throws Throwable {
        if (zkEnabled) {
            zkSend(producerData);
        } else {
            configSend(producerData);
        }
    }
    
    public void zkSend(ProducerData<K, V>... producerData) throws Throwable {
        List<ProducerPoolData<V>> list = new ArrayList<>();
        for (ProducerData<K, V> proData : producerData) {
            // 开始遍历发送的每一条数据
            Partition brokerIdPartition = null;
            Broker brokerInfoOpt = null;
            int numRetries = 0;
            while (numRetries <= config.zkReadRetries && brokerInfoOpt == null) {
                if (numRetries > 0) {
                    logger.info("Try #" + numRetries + " ZK producer cache is stale. Refreshing it by reading from ZK again");
                    brokerPartitionInfo.updateInfo();
                }
                
                List<Partition> topicPartitionsList = getPartitionListForTopic(proData); // 获取topic的分区列表
                int totalNumPartitions = topicPartitionsList.size();
                
                int partitionId = getPartition(proData.getKey(), totalNumPartitions); // 根据key，觉得要发送到哪一个分区中
                brokerIdPartition = topicPartitionsList.get(partitionId);
                brokerInfoOpt = brokerPartitionInfo.getBrokerInfo(brokerIdPartition.brokerId);
                numRetries += 1;
            }
            // 判断broker的信息
            if (brokerInfoOpt != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Sending message to broker " + brokerInfoOpt.host + ":" + brokerInfoOpt.port + " on partition " + brokerIdPartition.partId);
                }
            } else {
                throw new NoBrokersForPartitionException("Invalid Zookeeper state. Failed to get partition for topic: " + proData.getTopic() + " and key: " + proData.getKey());
            }
            // 封装Partition对象，计算出来，数据要发送到哪一个分区，将数据分配到对应的分区中
            Partition partition = new Partition(brokerIdPartition.brokerId, brokerIdPartition.partId); // 哪一个broker上，对应的分区
            ProducerPoolData data = producerPool.getProducerPoolData(proData.getTopic(), partition, proData.getData()); // 最后决定，数据要发送到哪一个broker那一个分区上
            list.add(data);
        }
        
        ProducerPoolData<V>[] producerPoolRequests = new ProducerPoolData[list.size()];
        producerPool.send(list.toArray(producerPoolRequests));
    }
    
    public void configSend(ProducerData<K, V>... producerData) throws Throwable {
        List<ProducerPoolData<V>> list = new ArrayList<>();
        for (ProducerData<K, V> pd : producerData) {
            // find the broker partitions registered for this topic
            List<Partition> topicPartitionsList = getPartitionListForTopic(pd);
            int totalNumPartitions = topicPartitionsList.size();
            
            int randomBrokerId = random.nextInt(totalNumPartitions);
            Partition brokerIdPartition = topicPartitionsList.get(randomBrokerId);
            Broker brokerInfo = brokerPartitionInfo.getBrokerInfo(brokerIdPartition.brokerId);
            
            if (logger.isDebugEnabled()) {
                logger.debug("Sending message to broker " + brokerInfo.host + ":" + brokerInfo.port + " on a randomly chosen partition");
            }
            int partition = ProducerRequest.RandomPartition;
            if (logger.isDebugEnabled()) {
                logger.debug("Sending message to broker " + brokerInfo.host + ":" + brokerInfo.port + " on a partition " + brokerIdPartition.partId);
            }
            ProducerPoolData data = producerPool.getProducerPoolData(pd.getTopic(), new Partition(brokerIdPartition.brokerId, partition), pd.getData());
            list.add(data);
        }
        
        ProducerPoolData<V>[] producerPoolRequests = new ProducerPoolData[list.size()];
        producerPool.send(list.toArray(producerPoolRequests));
    }
    
    public void close() {
        boolean canShutdown = hasShutdown.compareAndSet(false, true);
        if (canShutdown) {
             producerPool.close();
             brokerPartitionInfo.close();
        }
    }
    
    private List<Partition> getPartitionListForTopic(ProducerData<K, V> pd) {
        if (logger.isDebugEnabled()) {
            logger.debug("Getting the number of broker partitions registered for topic: " + pd.getTopic());
        }
        SortedSet<Partition> topicPartitionsList = brokerPartitionInfo.getBrokerPartitionInfo(pd.getTopic()); // 根据数据的topic，找出对应的分区列表
        if (logger.isDebugEnabled()) {
            logger.debug("Broker partitions registered for topic: " + pd.getTopic() + " = " + topicPartitionsList);
        }
        int totalNumPartitions = topicPartitionsList.size();
        if (totalNumPartitions == 0) {
            throw new NoBrokersForPartitionException("Partition = " + pd.getKey());
        }
        return (List<Partition>) topicPartitionsList;
    }
    
}
