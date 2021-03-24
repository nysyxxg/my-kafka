package kafka.utils;

import java.util.concurrent.TimeUnit;

public class MockTime implements Time {
    private volatile Long currentMs;
    
    public MockTime(){
        this(System.currentTimeMillis());
    }
    public MockTime(Long currentMs) {
        this.currentMs = currentMs;
    }
    
    
    @Override
    public Long milliseconds() {
        return currentMs;
    }
    
    @Override
    public Long nanoseconds() {
        return  TimeUnit.NANOSECONDS.convert(currentMs, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public void sleep(Long ms) {
        currentMs += ms;
    }
}
