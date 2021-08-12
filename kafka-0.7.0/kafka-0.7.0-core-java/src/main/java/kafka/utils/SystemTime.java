package kafka.utils;


public class SystemTime implements Time {
    
    public Long milliseconds() {
        return System.currentTimeMillis();
    }
    
    public static Long getMilliseconds() {
        return System.currentTimeMillis();
    }
    
    public Long nanoseconds() {
        return System.nanoTime();
    }
    
    public static void sleepByTime(Long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void sleep(Long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
}
