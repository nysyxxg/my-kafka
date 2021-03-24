package kafka.serializer;

import kafka.message.Message;

import java.nio.ByteBuffer;

public interface Decoder<T> {
    
    T toEvent(Message message);
    
}

class DefaultDecoder implements Decoder<Message> {
    public Message toEvent(Message message) {
        return message;
    }
}

class StringDecoder implements Decoder<String> {
    public String toEvent(Message message) {
        ByteBuffer buf = message.payload();
        byte arr[] = new byte[buf.remaining()];
        buf.get(arr);
        return new String(arr);
    }
}
