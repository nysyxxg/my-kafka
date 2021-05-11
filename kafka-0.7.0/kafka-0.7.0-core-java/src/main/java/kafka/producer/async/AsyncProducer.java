package kafka.producer.async;

import kafka.producer.ProducerPool;
import kafka.producer.SyncProducer;
import kafka.serializer.Encoder;

import java.util.Properties;

public class AsyncProducer<T> {
    public AsyncProducer(AsyncProducerConfig asyncProducerConfig, 
                         SyncProducer syncProducer, Encoder<T> serializer,
                         EventHandler<T> eventHandler, Properties eventHandlerProps,
                         CallbackHandler<T> cbkHandler, Properties cbkHandlerProps) {
    }
    
    public void close() {
    }
    
    public void start() {
    }
    
    public <V> void send(String topic,  T d, int partId) {
    }
}
