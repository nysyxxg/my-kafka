package kafka.producer.async;

import kafka.producer.ProducerConfig;
import kafka.producer.QueueItem;
import kafka.producer.SyncProducer;
import kafka.serializer.Encoder;

import java.util.List;
import java.util.Properties;

public class DefaultEventHandler<T> implements EventHandler<T> {
    public <T> DefaultEventHandler(ProducerConfig config, CallbackHandler<T> cbkHandler) {
    }
    
    @Override
    public void init(Properties props) {
    
    }
    
    @Override
    public void close() {
    
    }
    
    @Override
    public void handle(List<QueueItem> events, SyncProducer producer, Encoder encoder) {
    
    }
}
