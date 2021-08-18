package kafka.log;

public  interface LogStatsMBean {
    String getName();
    
    Long getSize();
    
    int getNumberOfSegments();
    
    Long getCurrentOffset();
    
    Long getNumAppendedMessages();
}