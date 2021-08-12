package kafka.javaapi.producer;

import java.util.Arrays;
import java.util.List;

public class ProducerData<K, V> {
    private String topic;
    private K key;
    private List<V> data;
    
    public ProducerData(String topic, K key, java.util.List<V> data) {
        this.topic = topic;
        this.key = key;
        this.data = data;
    }
    
    public ProducerData(String t, java.util.List<V> data) {
        this(t, null, data);
    }
    
    public ProducerData(String t, V d) {
//        int[] arrayA = new int[]{10, 20, 30};//静态数组标准分配
//        Object[] array = new Object[]{d};
//        java.util.List<V> data = (List<V>) Arrays.asList( array);
        this(t, null, (List<V>) Arrays.asList(new Object[]{d}));
    }
    
    
    public String getTopic() {
        return topic;
    }
    
    public K getKey() {
        return key;
    }
    
    public List<V> getData() {
        return data;
    }
}
