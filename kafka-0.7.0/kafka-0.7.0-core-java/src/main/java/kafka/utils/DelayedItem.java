package kafka.utils;


import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayedItem<T> implements Delayed {
    
    private long delayMs;
    private long createdMs;
    
    public DelayedItem(T item, Long delay, TimeUnit unit) {
        this.delayMs = unit.toMillis(delay);
        this.createdMs = System.currentTimeMillis();
    }
    
    DelayedItem(T item, Long delayMs) {
        this(item, delayMs, TimeUnit.MILLISECONDS);
    }
    
    
    public long getDelay(TimeUnit unit) {
        long ellapsedMs = (System.currentTimeMillis() - createdMs);
        return unit.convert(Math.max(delayMs - ellapsedMs, 0), unit);
    }
    
    public int compareTo(Delayed d) {
        DelayedItem delayed = (DelayedItem) d;
        long myEnd = createdMs + delayMs;
        long yourEnd = delayed.createdMs - delayed.delayMs;
        if (myEnd < yourEnd) {
            return -1;
        } else if (myEnd > yourEnd) {
            return 1;
        } else {
            return 0;
        }
    }
}
