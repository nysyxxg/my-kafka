package kafka.javaapi.producer.async;

import kafka.javaapi.producer.SyncProducer;
import kafka.producer.QueueItem;
import kafka.serializer.Encoder;

import java.util.List;
import java.util.Properties;

public interface EventHandler<T> {
 
    public void init(Properties props);

    public void handle(List<QueueItem<T>> events, SyncProducer producer, Encoder<T> encoder);

    public void close();
}
