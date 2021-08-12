package kafka.consumer;

import kafka.serializer.Decoder;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public abstract class ConsumerConnector {
    
    abstract void commitOffsets();
    
    public abstract void shutdown();
    
    public  abstract <T> Map<String, List<KafkaMessageStream<T>>> createMessageStreams(Map<String, Integer> topicCountMap, Decoder<T> decoder)
            throws UnknownHostException;
}
