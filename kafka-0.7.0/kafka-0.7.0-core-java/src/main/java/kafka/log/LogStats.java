package kafka.log;

import java.util.concurrent.atomic.AtomicLong;

interface LogStatsMBean {
    String getName();
    
    Long getSize();
    
    int getNumberOfSegments();
    
    Long getCurrentOffset();
    
    Long getNumAppendedMessages();
}


public class LogStats implements LogStatsMBean {
    Log log;
    
    LogStats(Log log) {
        this.log = log;
    }
    
    private AtomicLong numCumulatedMessages = new AtomicLong(0);
    
    public String getName() {
        return log.name;
    }
    
    public Long getSize() {
        return log.size();
    }
    
    public int getNumberOfSegments() {
        return log.numberOfSegments();
    }
    
    public Long getCurrentOffset() {
        return log.getHighwaterMark();
    }
    
    public Long getNumAppendedMessages() {
        return numCumulatedMessages.get();
    }
    
    public Long recordAppendedMessages(int nMessages) {
        return numCumulatedMessages.getAndAdd(nMessages);
    }
    
}
