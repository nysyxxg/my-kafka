package kafka.common;

public class MessageSizeTooLargeException extends RuntimeException   {
    
    public MessageSizeTooLargeException(String message) {
        super(message);
    }
    
}