package kafka.common;

public class UnknownMagicByteException extends RuntimeException {
    
    public UnknownMagicByteException(String message) {
        super(message);
    }
}
