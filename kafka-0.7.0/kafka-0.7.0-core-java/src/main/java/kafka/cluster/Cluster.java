package kafka.cluster;

import java.util.HashMap;
import java.util.List;

public class Cluster {
    
    private HashMap<Integer, Broker> brokers = new HashMap();
    
    public Cluster(List<Broker> brokerList) {
        for (Broker broker : brokerList) {
            brokers.put(broker.getId(), broker);
        }
    }
    
    public Cluster() {
    }
    
    public Broker getBroker(int id) {
        return brokers.get(id);
    }
    
    public void add(Broker broker) {
        brokers.put(broker.getId(), broker);
    }
    
    public void remove(int id) {
        brokers.remove(id);
    }
    
    public int size = brokers.size();
    
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        for (Broker broker : brokers.values()) {
            sb.append(broker).append(",");
        }
        return "Cluster(" + sb.toString() + ")";
    }
    
}
