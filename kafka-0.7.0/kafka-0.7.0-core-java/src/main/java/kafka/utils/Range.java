package kafka.utils;

public abstract class Range {
    
    public abstract Long start();
    
    public abstract Long size();
    
    Boolean isEmpty() {
        return size() == 0;
    }
    
    Boolean contains(Long value) {
        if ((start() == 0 && value == start()) ||
                (size() > 0 && value >= start() && value <= start() + size() - 1))
            return true;
        else
            return false;
    }
    
    public String toString() {
        return "(start=" + start() + ", size=" + size() + ")";
    }
    
}
