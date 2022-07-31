package kafka.javaapi;

import kafka.javaapi.producer.SyncProducer;
import kafka.producer.QueueItem;
import kafka.serializer.Encoder;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Properties;


public class Implicits {
    
    private static Logger logger = Logger.getLogger(Implicits.class);
    
    public static kafka.message.ByteBufferMessageSet javaMessageSetToScalaMessageSet(
            kafka.javaapi.message.ByteBufferMessageSet messageSet) {
        return messageSet.getUnderlying();
    }
    
    public static kafka.javaapi.message.ByteBufferMessageSet scalaMessageSetToJavaMessageSet(
            kafka.message.ByteBufferMessageSet messageSet) throws Throwable {
        return new kafka.javaapi.message.ByteBufferMessageSet(messageSet.getBuffer(), messageSet.getInitialOffset(), messageSet.getErrorCode());
    }
    
    public static kafka.javaapi.producer.SyncProducer toJavaSyncProducer(kafka.producer.SyncProducer producer) {
        if (logger.isDebugEnabled()) {
            logger.debug("Implicit instantiation of Java Sync Producer");
        }
        return new kafka.javaapi.producer.SyncProducer(producer);
    }
    
    public static kafka.producer.SyncProducer toSyncProducer(kafka.javaapi.producer.SyncProducer producer) {
        if (logger.isDebugEnabled()) {
            logger.debug("Implicit instantiation of Sync Producer");
        }
        return producer.underlying;
    }
    
    public static kafka.producer.async.EventHandler toScalaEventHandler(kafka.javaapi.producer.async.EventHandler eventHandler) {
        
        return new kafka.producer.async.EventHandler() {
            @Override
            public void init(java.util.Properties props) {
                eventHandler.init(props);
            }
            @Override
            public void handle(List events, kafka.producer.SyncProducer producer, Encoder encoder) {
//        import collection.JavaConversions._
//        eventHandler.handle(JavaConversions.asList(events), producer, encoder)
                kafka.javaapi.producer.SyncProducer producer2 = toJavaSyncProducer(producer); // 将 kafka.producer.SyncProducer 转化 producer;
                eventHandler.handle(events, producer2, encoder);
            }
            @Override
            public void close() {
                eventHandler.close();
            }
        };
    }
    
    public static kafka.javaapi.producer.async.EventHandler toJavaEventHandler(
            kafka.producer.async.EventHandler eventHandler) {
        return new kafka.javaapi.producer.async.EventHandler() {
            
            @Override
            public void init(Properties props) {
                eventHandler.init(props);
            }
            
            @Override
            public void close() {
                eventHandler.close();
            }
            
            @Override
            public void handle(List events, SyncProducer producer, Encoder encoder) {
                kafka.producer.SyncProducer producer2 = toSyncProducer(producer);// kafka.javaapi.producer.SyncProducer 转换
                eventHandler.handle(events, producer2, encoder);
            }
        };
    }
    
    public static kafka.producer.async.CallbackHandler toScalaCbkHandler(
            kafka.javaapi.producer.async.CallbackHandler cbkHandler) {
        return new kafka.producer.async.CallbackHandler() {
            public void afterEnqueue(QueueItem data, Boolean added) {
                cbkHandler.afterEnqueue(data, added);
            }
            
            @Override
            public void init(Properties props) {
                cbkHandler.init(props);
            }
            
            @Override
            public void afterEnqueue(QueueItem data, boolean added) {
                cbkHandler.afterEnqueue(data, added);
            }
            
            @Override
            public QueueItem beforeEnqueue(QueueItem data, Boolean added) {
                return cbkHandler.beforeEnqueue(data);
            }
            
            @Override
            public List<QueueItem> afterDequeuingExistingData(QueueItem data) {
                return cbkHandler.afterDequeuingExistingData(data);
            }
            
            @Override
            public List<QueueItem> lastBatchBeforeClose() {
                return cbkHandler.lastBatchBeforeClose();
            }
            
            @Override
            public void close() {
                cbkHandler.close();
            }
            
            @Override
            public List<QueueItem> beforeSendingData(List data) {
                return cbkHandler.beforeSendingData(data);
            }
        };
    }
    
    
    public static kafka.javaapi.producer.async.CallbackHandler toJavaCbkHandler(kafka.producer.async.CallbackHandler cbkHandler) {
       
        return new kafka.javaapi.producer.async.CallbackHandler() {
    
            @Override
            public void init(java.util.Properties props) {
                cbkHandler.init(props);
            }
    
            @Override
            public QueueItem beforeEnqueue(QueueItem data) {
                return cbkHandler.beforeEnqueue(data, true);
            }
    
            @Override
            public void afterEnqueue(QueueItem data, boolean added) {
                cbkHandler.afterEnqueue(data, added);
            }
            @Override
            public List<QueueItem> afterDequeuingExistingData(QueueItem data) {
                return cbkHandler.afterDequeuingExistingData(data);
            }
            @Override
            public List<QueueItem> lastBatchBeforeClose() {
                return cbkHandler.lastBatchBeforeClose();
            }
    
            @Override
            public void close() {
                cbkHandler.close();
            }
    
            @Override
            public List<QueueItem> beforeSendingData(List data) {
                return cbkHandler.beforeSendingData(data);
            }
        };
    }
    
    public static kafka.api.MultiFetchResponse toMultiFetchResponse(kafka.javaapi.MultiFetchResponse response) {
        return response.underlying;
    }
    
    public static kafka.javaapi.MultiFetchResponse toJavaMultiFetchResponse(kafka.api.MultiFetchResponse response) {
        return new kafka.javaapi.MultiFetchResponse(response.buffer, response.numSets, response.offsets);
    }
}
