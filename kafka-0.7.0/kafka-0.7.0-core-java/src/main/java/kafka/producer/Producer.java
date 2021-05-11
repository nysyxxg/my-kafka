package kafka.producer;

import kafka.serializer.Encoder;
import kafka.utils.Utils;
import org.apache.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;


import kafka.producer.async.CallbackHandler;
import kafka.producer.async.EventHandler;
import org.apache.log4j.Logger;
import kafka.serializer.Encoder;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import kafka.api.ProducerRequest;

public class Producer<K, V> {
    
    private Logger logger = Logger.getLogger(Producer.class);
    private AtomicBoolean hasShutdown = new AtomicBoolean(false);
    
    ProducerConfig producerConfig;
    Partitioner<K> partitioner;
    ProducerPool<V> producerPool;
    Boolean populateProducerPool;
    BrokerPartitionInfo brokerPartitionInfo;

    public Producer(ProducerConfig producerConfig) {
        this.producerConfig = producerConfig;
    }
    
    public Producer(ProducerConfig producerConfig,
                    Partitioner<K> partitioner,
                    ProducerPool<V> producerPool,
                    Boolean populateProducerPool,
                    BrokerPartitionInfo brokerPartitionInfo) {
        this.producerConfig = producerConfig;
        
        this.partitioner = partitioner;
        this.producerPool = producerPool;
        this.populateProducerPool = populateProducerPool;
        this.brokerPartitionInfo = brokerPartitionInfo;
        
    }
    
    Producer(ProducerConfig config, Partitioner partitioner, Encoder encoder)
            throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException {
//        Partitioner partitioner = (Partitioner) Utils.getObject(config.partitionerClass);
//        Encoder encoder =  (Encoder)Utils.getObject(config.serializerClass);
        this(config, partitioner, new ProducerPool<V>(config, encoder), true, null);
    }
    
    
    Producer(ProducerConfig config,
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
    
    public void close() {
        boolean canShutdown = hasShutdown.compareAndSet(false, true);
        if (canShutdown) {
            // producerPool.close;
            // brokerPartitionInfo.close;
        }
    }
    
    
    public void send(V producerData) {
    }
}
