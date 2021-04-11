package kafka.consumer.storage;

import scala.Tuple2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MemoryOffsetStorage implements OffsetStorage {
    ConcurrentHashMap<Tuple2<Integer, String>, Tuple2<AtomicLong, Lock>> offsetAndLock = new ConcurrentHashMap();
    
    @Override
    public Long reserve(int node, String topic) {
        
        Tuple2 key = new Tuple2<Integer, String>(node, topic);
        if (!offsetAndLock.containsKey(key)) {
            offsetAndLock.putIfAbsent(key, new Tuple2(new AtomicLong(0), new ReentrantLock()));
        }
        Tuple2<AtomicLong, Lock> tuple2 = offsetAndLock.get(key);
        tuple2._2.lock();
        return tuple2._1.get();
    }
    
    @Override
    public void commit(int node, String topic, Long offset) {
        Tuple2<AtomicLong, Lock> tuple2 = offsetAndLock.get(new Tuple2<>(node, topic));
        
        AtomicLong highwater = tuple2._1;
        Lock lock = tuple2._2;
        highwater.set(offset);
        lock.unlock();
//        offset;
        return;
    }
}
