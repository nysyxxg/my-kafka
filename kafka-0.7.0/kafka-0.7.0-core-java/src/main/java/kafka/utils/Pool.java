package kafka.utils;

import scala.Tuple2;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class Pool<K, V> implements Iterable<Tuple2<K, V>> {
    
    private ConcurrentHashMap<K, V> pool = new ConcurrentHashMap<K, V>();
    
    public Pool() {
    }
    
    public Pool(ConcurrentHashMap<K, V> map) {
        for (K key : map.keySet()) {
            pool.put(key, map.get(key));
        }
    }
    
    public ConcurrentHashMap.KeySetView<K, V> keys() {
        return pool.keySet();
    }
    
    public Iterable<V> values() {
        return new ArrayList<V>(pool.values());
    }
    
    public int size() {
        return pool.size();
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
        return pool.get(key);
    }
    
    public V remove(K key) {
        return (V) pool.remove(key);
    }
    
    
    public void clear() {
        pool.clear();
    }
    
    @Override
    public Iterator<Tuple2<K, V>> iterator() {
        return new Iterator<Tuple2<K, V>>() {
            private Iterator<Map.Entry<K, V>> iter = pool.entrySet().iterator();
            
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }
            
            @Override
            public Tuple2<K, V> next() {
                Map.Entry<K, V> n = iter.next();
                return new Tuple2<K, V>(n.getKey(), n.getValue());
            }
            
            @Override
            public void remove() {
            
            }
            
            @Override
            public void forEachRemaining(Consumer action) {
            
            }
        };
    }
}
