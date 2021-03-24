package kafka.utils;

import org.apache.log4j.Logger;

@nonthreadsafe
public class Throttler {
    
    private Logger logger = Logger.getLogger(Throttler.class);
    
    private static Long DefaultCheckIntervalMs = 100L;
    Double desiredRatePerSec;
    Long checkIntervalMs;
    Boolean throttleDown;
    Time time;
    
    private Object lock = new Object();
    private Long periodStartNs = time.nanoseconds();
    private Double observedSoFar = 0.0;
    
    Throttler(Double desiredRatePerSec, Boolean throttleDown) {
        this(desiredRatePerSec, DefaultCheckIntervalMs, throttleDown, new SystemTime());
    }
    
    Throttler(Double desiredRatePerSec,
              Long checkIntervalMs,
              Boolean throttleDown,
              Time time) {
        this.checkIntervalMs = checkIntervalMs;
        this.desiredRatePerSec = desiredRatePerSec;
        this.throttleDown = throttleDown;
        this.time = time;
    }
    
    
    Throttler(Double desiredRatePerSec) {
        this(desiredRatePerSec, DefaultCheckIntervalMs, true, new SystemTime());
    }
    
    void maybeThrottle(Double observed) {
        synchronized (lock) {
            observedSoFar += observed;
            Long now = time.nanoseconds();
            Long ellapsedNs = now - periodStartNs;
            // if we have completed an interval AND we have observed something, maybe
            // we should take a little nap
            if (ellapsedNs > checkIntervalMs * Time.NsPerMs && observedSoFar > 0) {
                double rateInSecs = (observedSoFar * Time.NsPerSec) / ellapsedNs;
                boolean needAdjustment = !(throttleDown ^ (rateInSecs > desiredRatePerSec));
                if (needAdjustment) {
                    // solve for the amount of time to sleep to make us hit the desired rate
                    Double desiredRateMs = desiredRatePerSec / Time.MsPerSec;
                    long ellapsedMs = ellapsedNs / Time.NsPerMs;
                    long sleepTime = Math.round(observedSoFar / desiredRateMs - ellapsedMs);
                    if (sleepTime > 0) {
                        if (logger.isDebugEnabled()) ;
                        logger.debug("Natural rate is " + rateInSecs + " per second but desired rate is " + desiredRatePerSec +
                                ", sleeping for " + sleepTime + " ms to compensate.");
                        time.sleep(sleepTime);
                    }
                }
                periodStartNs = now;
                observedSoFar = 0.0;
            }
        }
    }
    
}
