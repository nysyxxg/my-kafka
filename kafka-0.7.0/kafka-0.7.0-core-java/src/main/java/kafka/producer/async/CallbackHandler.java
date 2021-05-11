package kafka.producer.async;

import kafka.producer.QueueItem;

import java.util.List;
import java.util.Properties;

public interface CallbackHandler<T> {
    
    void init(Properties props  );
    
    
    QueueItem<T> beforeEnqueue(QueueItem   data ,Boolean added  );
    
    List<QueueItem> afterDequeuingExistingData(QueueItem data );
    
    List<QueueItem> beforeSendingData(List<QueueItem<T>> data);
    
    List<QueueItem> lastBatchBeforeClose();
    
    void close();
}
