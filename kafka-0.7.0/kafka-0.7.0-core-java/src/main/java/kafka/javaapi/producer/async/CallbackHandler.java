package kafka.javaapi.producer.async;


import kafka.producer.QueueItem;

import java.util.List;
import java.util.Properties;

public interface CallbackHandler<T> {
    
    public void init(Properties props);
    
    public QueueItem<T> beforeEnqueue(QueueItem<T> data);
  
    public void afterEnqueue(QueueItem<T> data, boolean added);

    public java.util.List<QueueItem<T>> afterDequeuingExistingData(QueueItem<T> data);

    public java.util.List<QueueItem<T>> beforeSendingData(java.util.List<QueueItem<T>> data);
 
    public List<QueueItem> lastBatchBeforeClose();
    
    public void close();
}
