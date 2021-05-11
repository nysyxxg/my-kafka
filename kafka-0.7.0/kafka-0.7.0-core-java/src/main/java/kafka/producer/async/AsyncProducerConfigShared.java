package kafka.producer.async;

import kafka.producer.SyncProducerConfigShared;
import kafka.utils.Utils;

import java.util.Properties;

public abstract class AsyncProducerConfigShared extends SyncProducerConfigShared {
    Properties props;
    int queueTime;
    
    int queueSize;
    
    int enqueueTimeoutMs;
    
    int batchSize;
    
    public String serializerClass;
    
    public String cbkHandler;
    
    public Properties cbkHandlerProps;
    
    public String eventHandler;
    
    public Properties eventHandlerProps;
    
    public AsyncProducerConfigShared(Properties props) {
        super(props);
        this.props = props;
        
        /* maximum time, in milliseconds, for buffering data on the producer queue */
        this.queueTime = Utils.getInt(props, "queue.time", 5000);
        
        /** the maximum size of the blocking queue for buffering on the producer */
        this.queueSize = Utils.getInt(props, "queue.size", 10000);
        
        /**
         * Timeout for event enqueue:
         * 0: events will be enqueued immediately or dropped if the queue is full
         * -ve: enqueue will block indefinitely if the queue is full
         * +ve: enqueue will block up to this many milliseconds if the queue is full
         */
        this.enqueueTimeoutMs = Utils.getInt(props, "queue.enqueueTimeout.ms", 0);
        
        /** the number of messages batched at the producer */
        this.batchSize = Utils.getInt(props, "batch.size", 200);
        
        /** the serializer class for events */
        this.serializerClass = Utils.getString(props, "serializer.class", "kafka.serializer.DefaultEncoder");
        
        /** the callback handler for one or multiple events */
        this.cbkHandler = Utils.getString(props, "callback.handler", null);
        
        /** properties required to initialize the callback handler */
        this.cbkHandlerProps = Utils.getProps(props, "callback.handler.props", null);
        
        /** the handler for events */
        this.eventHandler = Utils.getString(props, "event.handler", null);
        
        /** properties required to initialize the callback handler */
        this.eventHandlerProps = Utils.getProps(props, "event.handler.props", null);
    }
    
    public AsyncProducerConfigShared() {
        super();
    }
    
    
}
