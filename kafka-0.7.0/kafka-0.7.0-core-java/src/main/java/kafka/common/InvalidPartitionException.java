package kafka.common;

public class InvalidPartitionException extends RuntimeException {
    public InvalidPartitionException(){
    
    }
    public InvalidPartitionException(String message) {
        super(message);
    }
    
}
