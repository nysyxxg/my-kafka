package kafka.serializer;

import kafka.message.Message;

public class StringEncoder implements Encoder<String> {
    public Message toMessage(String event) {
        return new Message(event.getBytes());
    }
}
