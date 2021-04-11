package kafka.common;

public class InvalidConfigException  extends RuntimeException {
    
    public InvalidConfigException(String message){
       super(message);
    }
}
