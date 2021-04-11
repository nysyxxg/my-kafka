package kafka.consumer.storage;

public interface OffsetStorage {
    
    Long reserve(int node, String topic);
    
    void commit(int node, String topic, Long offset);
    
}
