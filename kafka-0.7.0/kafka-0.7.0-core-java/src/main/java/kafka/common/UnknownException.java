package kafka.common;

public class UnknownException  extends  RuntimeException{
    public UnknownException(){
    
    }
    public UnknownException(String message) {
        super(message);
    }
    
    
}
