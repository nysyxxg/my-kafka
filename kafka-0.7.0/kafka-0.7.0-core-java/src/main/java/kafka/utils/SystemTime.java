package kafka.utils;


public class SystemTime implements Time {
    
    public Long milliseconds() {
        return System.currentTimeMillis();
    }
    
    public Long nanoseconds() {
        return  System.nanoTime();
    }
    
    public void sleep(Long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    
}
