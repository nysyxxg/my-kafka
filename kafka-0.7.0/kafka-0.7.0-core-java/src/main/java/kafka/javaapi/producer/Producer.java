package kafka.javaapi.producer;

import kafka.javaapi.Implicits;
import kafka.producer.Partitioner;
import kafka.producer.ProducerConfig;
import kafka.producer.ProducerPool;
import kafka.producer.QueueItem;
import kafka.serializer.Encoder;
import kafka.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Producer<K, V> {
    private ProducerConfig config;
    private Partitioner<K> partitioner;
    private ProducerPool<V> producerPool;
    private Boolean populateProducerPool = true;
    
    private kafka.producer.Producer underlying;
    
    
    public Producer(ProducerConfig config,
                    Partitioner<K> partitioner,
                    ProducerPool<V> producerPool,
                    Boolean populateProducerPool) {
        this.config = config;
        this.partitioner = partitioner;
        this.producerPool = producerPool;
        this.populateProducerPool = populateProducerPool;
        
        this.underlying = new kafka.producer.Producer<K, V>(config, partitioner, producerPool, populateProducerPool, null);
    }
    
    public Producer(ProducerConfig config) {
        this(config, (Partitioner) Utils.getObject(config.partitionerClass), // 默认的分区类
                new ProducerPool(config, (Encoder) Utils.getObject(config.serializerClass)), true); // 初始化Producer的时候，就会初始化ProducerPool
    }
    
    
    public Producer(ProducerConfig config,
                    Encoder<V> encoder,
                    kafka.javaapi.producer.async.EventHandler<V> eventHandler,
                    kafka.javaapi.producer.async.CallbackHandler<V> cbkHandler,
                    Partitioner<K> partitioner) {
        this(config, partitioner, new ProducerPool<V>(config, encoder,
                new kafka.producer.async.EventHandler<V>() {
                    public void init(Properties props) {
                        eventHandler.init(props);
                    }
                    
                    public void handle(List<QueueItem<V>> events, kafka.producer.SyncProducer producer,
                                       Encoder<V> encoder) {
//                                 import collection.JavaConversions._
//                                 import kafka.javaapi.Implicits._
                        eventHandler.handle(events, Implicits.toJavaSyncProducer(producer), encoder);
                    }
                    
                    public void close() {
                        eventHandler.close();
                    }
                },
                new kafka.producer.async.CallbackHandler<V>() {
                    public void init(Properties props) {
                        cbkHandler.init(props);
                    }
                    
                    @Override
                    public void afterEnqueue(QueueItem data, boolean added) {
                        cbkHandler.afterEnqueue(data, added);
                    }
                    
                    @Override
                    public QueueItem<V> beforeEnqueue(QueueItem data, Boolean added) {
                        return cbkHandler.beforeEnqueue(data);
                    }
                    
                    public List<QueueItem> afterDequeuingExistingData(QueueItem data) {
                        return cbkHandler.afterDequeuingExistingData(data);
                    }
                    
                    public List<QueueItem<V>> beforeSendingData(List<QueueItem<V>> data) {
                        return cbkHandler.beforeSendingData(data);
                    }
                    
                    public List<QueueItem> lastBatchBeforeClose() {
                        return cbkHandler.lastBatchBeforeClose();
                    }
                    
                    public void close() {
                        cbkHandler.close();
                    }
                }), true);
    }
    
    public void send(kafka.javaapi.producer.ProducerData<K, V> producerData) throws Throwable {
        underlying.send(new kafka.producer.ProducerData<K, V>(producerData.getTopic(), producerData.getKey(), producerData.getData()));
    }
    
    
    public void send(java.util.List<kafka.javaapi.producer.ProducerData<K, V>> producerData) throws Throwable {
        List<kafka.producer.ProducerData> list = new ArrayList();
        for (kafka.javaapi.producer.ProducerData<K, V> kvProducerData : producerData) {
            kafka.producer.ProducerData data = new kafka.producer.ProducerData<K, V>(kvProducerData.getTopic(), kvProducerData.getKey(), kvProducerData.getData());
            list.add(data);
        }
        kafka.producer.ProducerData<K,V>[] producerPoolRequests = new kafka.producer.ProducerData[list.size()];
        underlying.send(list.toArray(producerPoolRequests));
    }
    
    
    public void close() {
        underlying.close();
    }
    
}
