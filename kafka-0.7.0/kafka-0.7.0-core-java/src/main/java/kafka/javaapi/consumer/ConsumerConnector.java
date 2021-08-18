package kafka.javaapi.consumer;

import kafka.consumer.KafkaMessageStream;
import kafka.message.Message;
import kafka.serializer.Decoder;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public interface ConsumerConnector {
    
    public <T> Map<String, List<KafkaMessageStream<T>>> createMessageStreams(
            Map<String, Integer> topicCountMap, Decoder<T> decoder) throws UnknownHostException;
    
    public Map<String, List<KafkaMessageStream<Message>>> createMessageStreams(
            Map<String, Integer> topicCountMap);
    
    
    public void commitOffsets();
    
    public void shutdown();
}