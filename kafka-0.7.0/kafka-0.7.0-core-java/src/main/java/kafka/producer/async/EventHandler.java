package kafka.producer.async;

import kafka.producer.QueueItem;
import kafka.producer.SyncProducer;

import java.util.List;
import java.util.Properties;
import kafka.serializer.Encoder;

public interface EventHandler<T> {
    
    void init(Properties props  );
    
    void close();
    
    void handle(List<QueueItem> events, SyncProducer producer , Encoder encoder);
}
