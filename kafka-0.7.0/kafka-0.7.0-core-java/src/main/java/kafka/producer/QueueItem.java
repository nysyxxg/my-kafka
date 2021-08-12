package kafka.producer;

public class QueueItem<T> {
    
    private T data;
    private String topic;
    private int partition;
    
    public QueueItem(T data, String topic, int partition) {
        this.data = data;
        this.topic = topic;
        this.partition = partition;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public java.lang.String getTopic() {
        return topic;
    }
    
    public void setTopic(java.lang.String topic) {
        this.topic = topic;
    }
    
    public int getPartition() {
        return partition;
    }
    
    public void setPartition(int partition) {
        this.partition = partition;
    }
    
    public String toString() {
        return "topic: " + topic + ", partition: " + partition + ", data: " + data.toString();
    }
    
}
