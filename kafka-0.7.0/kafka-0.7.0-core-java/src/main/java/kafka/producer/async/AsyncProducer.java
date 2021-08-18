package kafka.producer.async;

import kafka.api.ProducerRequest;
import kafka.producer.ProducerConfig;
import kafka.producer.ProducerPool;
import kafka.producer.QueueItem;
import kafka.producer.SyncProducer;
import kafka.serializer.Encoder;
import kafka.utils.Utils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncProducer<T> {
    private Logger logger = Logger.getLogger(AsyncProducer.class);
    private AtomicBoolean closed = new AtomicBoolean(false);
    private LinkedBlockingQueue<QueueItem<T>> queue;
    
    private Object shutdown = new Object();
    private  Random random = new Random();
    private String ProducerMBeanName = "kafka.producer.Producer:type=AsyncProducerStats";
    private ProducerSendThread sendThread;
    private AsyncProducerStats asyncProducerStats;
    
    
    AsyncProducerConfig config;
    SyncProducer producer;
    Encoder<T> serializer;
    EventHandler<T> eventHandler;
    Properties eventHandlerProps;
    CallbackHandler<T> cbkHandler;
    Properties cbkHandlerProps;
    
    public AsyncProducer(AsyncProducerConfig config) {
        this(config, new SyncProducer(config), (Encoder) Utils.getObject(config.serializerClass),
                (EventHandler) Utils.getObject(config.eventHandler),
                config.eventHandlerProps,
                (CallbackHandler) Utils.getObject(config.cbkHandler),
                config.cbkHandlerProps);
    }
    
    
    public AsyncProducer(AsyncProducerConfig config,
                         SyncProducer producer,
                         Encoder<T> serializer,
                         EventHandler<T> eventHandler,
                         Properties eventHandlerProps,
                         CallbackHandler<T> cbkHandler,
                         Properties cbkHandlerProps) {
        this.config = config;
        this.producer = producer;
        this.serializer = serializer;
        this.eventHandler = eventHandler;
        this.eventHandlerProps = eventHandlerProps;
        this.cbkHandler = cbkHandler;
        this.cbkHandlerProps = cbkHandlerProps;
        
        this.queue = new LinkedBlockingQueue<QueueItem<T>>(config.queueSize);
        
        if (eventHandler != null) {
            eventHandler.init(eventHandlerProps);
        }
        if (cbkHandler != null) {
            cbkHandler.init(cbkHandlerProps);
        }
        EventHandler<T> eventHandler2;
        if (eventHandler != null) {
            eventHandler2 = eventHandler;
        } else {
            eventHandler2 = new DefaultEventHandler<T>(new ProducerConfig(config.props), cbkHandler);
        }
        
        this.sendThread = new ProducerSendThread("ProducerSendThread-" + random.nextInt(), queue,
                serializer, producer, eventHandler2,
                cbkHandler, config.queueTime, config.batchSize, shutdown);// 实例化发送数据线程，从queue 获取发送event对象
        
        this.sendThread.setDaemon(false); // 设置为false，主方法执行完成并不会结束.
        //  thread.setDaemon(true);当为守护线程的时候,主方法结束,守护线程就会结束.
        
        this.asyncProducerStats = new AsyncProducerStats<T>(queue);
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            ObjectName objName = new ObjectName(ProducerMBeanName);
            if (mbs.isRegistered(objName)) {
                mbs.unregisterMBean(objName);
            }
            mbs.registerMBean(asyncProducerStats, objName);
        } catch (Exception e) {
            logger.warn("can't register AsyncProducerStats");
        }
        
    }
    
    public void start() {
        sendThread.start();
    }
    
    public void send(String topic, T event) {
        send(topic, event, ProducerRequest.RandomPartition);
    }
    
    public void send(String topic, T event, int randomPartition) {
    
    }
    
    public void close() {
        if (cbkHandler != null) {
            cbkHandler.close();
            logger.info("Closed the callback handler");
        }
        closed.set(true);
        try {
            queue.put(new QueueItem(shutdown, null, -1));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Added shutdown command to the queue");
        }
        sendThread.shutdown();
        sendThread.awaitShutdown();
        producer.close();
        logger.info("Closed AsyncProducer");
        
    }
    
    public void setLoggerLevel(Level level) {
        logger.setLevel(level);
    }
    
}
