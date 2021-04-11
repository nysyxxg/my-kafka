package kafka.utils;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class Pool<K, V> {
    
    private ConcurrentHashMap pool = new ConcurrentHashMap<>();
   
    public ConcurrentHashMap.KeySetView<K, V> keys = pool.keySet();
    
    public Iterable<V> values = new ArrayList<V>(pool.values());
    
    public int size = pool.size();
    
    public Pool() {
    }
    public Pool(ConcurrentHashMap map) {
        for (Object key : map.keySet()) {
            pool.put(key, map.get(key));
        }
    }
    
    public void put(K k, V v) {
        pool.put(k, v);
    }
    
    
    public V putIfNotExists(K k, V v) {
        pool.putIfAbsent(k, v);
        return v;
    }
    
    public boolean contains(K id) {
        return pool.containsKey(id);
    }
    
    public V get(K key) {
        return (V) pool.get(key);
    }
    
    public V remove(K key) {
        return (V) pool.remove(key);
    }
    
    
    public void clear() {
        pool.clear();
    }
}
