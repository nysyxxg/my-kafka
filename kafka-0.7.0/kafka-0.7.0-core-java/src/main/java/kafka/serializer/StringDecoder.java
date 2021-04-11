package kafka.serializer;

import kafka.message.Message;

import java.nio.ByteBuffer;

public class StringDecoder implements Decoder<String> {
    public String toEvent(Message message) {
        ByteBuffer buf = message.payload();
        byte arr[] = new byte[buf.remaining()];
        buf.get(arr);
        return new String(arr);
    }
}