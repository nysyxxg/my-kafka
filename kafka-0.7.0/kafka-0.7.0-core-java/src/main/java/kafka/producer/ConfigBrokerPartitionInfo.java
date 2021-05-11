package kafka.producer;

import kafka.cluster.Broker;
import kafka.cluster.Partition;
import kafka.common.InvalidConfigException;
import org.apache.log4j.Logger;
import scala.Tuple2;

import java.util.*;

public class ConfigBrokerPartitionInfo implements BrokerPartitionInfo {
    
    private Logger logger = Logger.getLogger(ConfigBrokerPartitionInfo.class);
    private ProducerConfig config;
    private SortedSet<Partition> brokerPartitions;
    private Map<Integer, Broker> allBrokers;
    
    
    public ConfigBrokerPartitionInfo(ProducerConfig config) {
        this.config = config;
        this.brokerPartitions = getConfigTopicPartitionInfo();
        this.allBrokers = getConfigBrokerInfo();
    }
    
    private SortedSet<Partition> getConfigTopicPartitionInfo() {
        String brokerInfoList[] = config.brokerList.split(",");
        if (brokerInfoList.length == 0) throw new InvalidConfigException("broker.list is empty");
        // check if each individual broker info is valid => (brokerId: brokerHost: brokerPort)
        for (String bInfo : brokerInfoList) {
            String brokerInfo[] = bInfo.split(":");
            if (brokerInfo.length < 3) {
                throw new InvalidConfigException("broker.list has invalid value");
            }
            
        }
        
        List<Tuple2<Integer, Integer>> brokerPartitions = new ArrayList<>();
        for (String bInfo : brokerInfoList) {
            String brokerInfo[] = bInfo.split(":");
            String head = brokerInfo[0];
            brokerPartitions.add(new Tuple2<>(Integer.parseInt(head), 1));
        }
        SortedSet<Partition> brokerParts = new TreeSet<Partition>();
        for (Tuple2<Integer, Integer> bp : brokerPartitions) {
            for (int i = 0; i < bp._2; i++) {
                Partition bidPid = new Partition(bp._1, i);
                brokerParts.add(bidPid);
            }
        }
        return brokerParts;
    }
    
    private Map<Integer, Broker> getConfigBrokerInfo() {
        Map<Integer, Broker> brokerInfo = new HashMap<Integer, Broker>();
        String brokerInfoList[] = config.brokerList.split(",");
        
        for (String bInfo : brokerInfoList) {
            String brokerIdHostPort[] = bInfo.split(":");
            int port = Integer.parseInt(brokerIdHostPort[0]);
            Broker broker = new Broker(port, brokerIdHostPort[1], brokerIdHostPort[1], Integer.parseInt(brokerIdHostPort[2]));
            brokerInfo.put(port, broker);
            
        }
        return brokerInfo;
    }
    
    @Override
    public SortedSet<Partition> getBrokerPartitionInfo(String topic) {
        return null;
    }
    
    @Override
    public Broker getBrokerInfo(int brokerId) {
        return allBrokers.get(brokerId);
    }
    
    @Override
    public Map<Integer, Broker> getAllBrokerInfo() {
        return allBrokers;
    }
    
    @Override
    public void updateInfo() {
    }
    
    @Override
    public void close() {
    }
}
