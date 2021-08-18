package kafka.serializer;

import kafka.message.Message;

public interface Encoder<T> {
    Message toMessage(T event);
}

