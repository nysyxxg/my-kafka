package kafka.serializer;

import kafka.message.Message;

import java.nio.ByteBuffer;

public interface Decoder<T> {
    
    T toEvent(Message message);
    
}




