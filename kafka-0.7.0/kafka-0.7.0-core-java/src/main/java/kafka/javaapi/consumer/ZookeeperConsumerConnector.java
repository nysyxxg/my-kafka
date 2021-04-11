package kafka.javaapi.consumer;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaMessageStream;
import kafka.message.Message;
import kafka.serializer.Decoder;

import java.util.List;
import java.util.Map;

public class ZookeeperConsumerConnector implements   ConsumerConnector {
    
    public ZookeeperConsumerConnector(ConsumerConfig config) {
        super();
    }
    
    @Override
    public <T> Map<String, List<KafkaMessageStream<T>>> createMessageStreams(Map<String, Integer> topicCountMap, Decoder<T> decoder) {
        return null;
    }
    
    @Override
    public Map<String, List<KafkaMessageStream<Message>>> createMessageStreams(Map<String, Integer> topicCountMap) {
        return null;
    }
    
    @Override
    public void commitOffsets() {
    
    }
    
    @Override
    public void shutdown() {
    
    }
}
