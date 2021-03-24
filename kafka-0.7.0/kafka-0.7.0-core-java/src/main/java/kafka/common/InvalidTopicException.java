package kafka.common;

public class InvalidTopicException extends RuntimeException   {
    
    public InvalidTopicException(String message) {
        super(message);
    }
    
}
