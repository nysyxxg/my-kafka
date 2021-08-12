package kafka.producer.async;

import kafka.producer.QueueItem;
import kafka.producer.SyncProducer;

import java.util.List;
import java.util.Properties;

import kafka.serializer.Encoder;

public interface EventHandler<T> {
    
    public void init(Properties props);
    
    public  void close();
    
    public  void handle(List<QueueItem<T>> events, SyncProducer producer, Encoder<T> encoder);
}
