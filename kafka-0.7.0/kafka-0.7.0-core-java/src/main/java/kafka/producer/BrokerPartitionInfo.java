package kafka.producer;

import kafka.cluster.Broker;
import kafka.cluster.Partition;

import java.util.Map;
import java.util.SortedSet;

public interface BrokerPartitionInfo {
    
    SortedSet<Partition> getBrokerPartitionInfo(String topic );
    
    Broker getBrokerInfo(int brokerId);
    
    Map<Integer, Broker> getAllBrokerInfo();
    
    void updateInfo();
    
    void close();
}
