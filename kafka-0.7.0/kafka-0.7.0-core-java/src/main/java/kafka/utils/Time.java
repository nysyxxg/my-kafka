package kafka.utils;

public interface Time {
    
    Long NsPerUs = 1000l;
    Long UsPerMs = 1000l;
    Long MsPerSec = 1000l;
    Long NsPerMs = NsPerUs * UsPerMs;
    Long NsPerSec = NsPerMs * MsPerSec;
    Long UsPerSec = UsPerMs * MsPerSec;
    Long SecsPerMin = 60l;
    Long MinsPerHour = 60l;
    Long HoursPerDay = 24l;
    Long SecsPerHour = SecsPerMin * MinsPerHour;
    Long SecsPerDay = SecsPerHour * HoursPerDay;
    Long MinsPerDay = MinsPerHour * HoursPerDay;
    
    
    Long milliseconds();
    
    Long nanoseconds();
    
    void sleep(Long ms);
    
}
