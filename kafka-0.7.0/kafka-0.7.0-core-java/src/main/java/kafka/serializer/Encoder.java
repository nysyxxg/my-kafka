package kafka.serializer;

import kafka.message.Message;

public interface Encoder<T> {
    
    Message toMessage(T event);
    
}


class DefaultEncoder implements Encoder<Message> {
    public Message toMessage(Message event) {
        return event;
    }
}

class StringEncoder implements Encoder<String> {
    public Message toMessage(String event) {
        return new Message(event.getBytes());
    }
}
