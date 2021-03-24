package kafka.common;

public class OffsetOutOfRangeException  extends RuntimeException {
    public OffsetOutOfRangeException(){
    
    }
    public OffsetOutOfRangeException(String message) {
        super(message);
    }
    
}
