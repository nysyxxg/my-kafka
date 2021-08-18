package kafka.serializer;

import kafka.message.Message;

public class DefaultEncoder implements Encoder<Message> {
    public Message toMessage(Message event) {
        return event;
    }
}