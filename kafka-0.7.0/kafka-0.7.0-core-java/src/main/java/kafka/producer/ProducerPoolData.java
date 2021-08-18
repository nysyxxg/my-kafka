package kafka.producer;

import kafka.cluster.Partition;

import java.util.List;

public class ProducerPoolData<V> {
    String topic;
    Partition bidPid;
    List<V> data;
    
    public ProducerPoolData(String topic,
                            Partition bidPid,
                            List<V> data) {
        this.bidPid = bidPid;
        this.topic = topic;
        this.data = data;
    }
    
    public ProducerPoolData() {
    }
    
    public String getTopic() {
        return topic;
    }
    
    public Partition getBidPid() {
        return bidPid;
    }
    
    public List<V> getData() {
        return data;
    }
}