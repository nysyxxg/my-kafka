package kafka.serializer;

import kafka.message.Message;

public class DefaultDecoder implements Decoder<Message> {
    public Message toEvent(Message message) {
        return message;
    }
}