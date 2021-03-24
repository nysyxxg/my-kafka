package kafka.common;

public class InvalidMessageSizeException extends RuntimeException {
    public InvalidMessageSizeException(){
    
    }
    public InvalidMessageSizeException(String message) {
        super(message);
    }
    
}
