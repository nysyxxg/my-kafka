package kafka.producer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProducerData<K, V> {
    String topic;
    K key;
    List<V> data;
    
    public   ProducerData(String topic, K key, List<V> data) {
        this.topic = topic;
        this.key = key;
        this.data = data;
    }
    
    ProducerData(String t, List<V> d) {
        this(t, null, d);
    }
    
    public ProducerData(String topic, V d) {
//        List<V> list = new ArrayList<>();
//        list.add(d);
        this(topic, null, Arrays.asList(d));
    }
    
    
    String getTopic() {
        return topic;
    }
    
    K getKey() {
        return key;
    }
    
    List<V> getData() {
        return data;
    }
    
}
