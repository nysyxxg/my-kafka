package kafka.common;

public class MessageLengthException extends RuntimeException{
    public MessageLengthException(){
    }
    
    public  MessageLengthException(String  message){
        super(message);
    }
}