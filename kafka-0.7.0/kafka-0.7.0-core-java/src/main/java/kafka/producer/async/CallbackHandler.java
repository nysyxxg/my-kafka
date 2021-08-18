package kafka.producer.async;

import kafka.producer.QueueItem;

import java.util.List;
import java.util.Properties;

public interface CallbackHandler<T> {
    
    void init(Properties props  );
    
    void afterEnqueue(QueueItem data, boolean added);
    
    QueueItem<T> beforeEnqueue(QueueItem   data ,Boolean added  );
    
    List<QueueItem<T>> afterDequeuingExistingData(QueueItem<T> data );
    
    List<QueueItem<T>> beforeSendingData(List<QueueItem<T>> data);
    
    List<QueueItem> lastBatchBeforeClose();
    
    void close();
    
  
}
