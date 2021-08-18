package kafka.producer.async;

import kafka.producer.SyncProducerConfig;
import kafka.utils.Utils;

import java.util.Properties;


public class AsyncProducerConfig extends SyncProducerConfig {
    Properties props;
    int queueTime;
    int queueSize;
    int enqueueTimeoutMs;
    int batchSize;
    String serializerClass;
    String cbkHandler;
    Properties cbkHandlerProps;
    String eventHandler;
    Properties eventHandlerProps;
    public AsyncProducerConfig(Properties props) {
        super(props);
        this.props = props;
        
          queueTime = Utils.getInt(props, "queue.time", 5000);
    
          queueSize = Utils.getInt(props, "queue.size", 10000);
    
          enqueueTimeoutMs = Utils.getInt(props, "queue.enqueueTimeout.ms", 0);
    
          batchSize = Utils.getInt(props, "batch.size", 200);
    
          serializerClass = Utils.getString(props, "serializer.class", "kafka.serializer.DefaultEncoder");
    
          cbkHandler = Utils.getString(props, "callback.handler", null);
    
          cbkHandlerProps = Utils.getProps(props, "callback.handler.props", null);
    
          eventHandler = Utils.getString(props, "event.handler", null);
    
          eventHandlerProps = Utils.getProps(props, "event.handler.props", null);
        
    }
  
}