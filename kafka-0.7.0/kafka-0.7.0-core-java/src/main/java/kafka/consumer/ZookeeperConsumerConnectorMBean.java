package kafka.consumer;

public interface ZookeeperConsumerConnectorMBean {
    
    String getPartOwnerStats();
    
    String getConsumerGroup();
    
    Long getOffsetLag(String topic, int brokerId, int partitionId);
    
    Long getConsumedOffset(String topic, int brokerId, int partitionId);
    
    Long getLatestOffset(String topic, int brokerId, int partitionId);
    
}
