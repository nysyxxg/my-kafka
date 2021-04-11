package kafka.consumer;

import kafka.serializer.Decoder;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public interface ConsumerConnector extends  ZookeeperConsumerConnectorMBean{
    void shutdown();
    
    Map<String, List<KafkaMessageStream<?>>>  createMessageStreams(Map<String, Integer> topicCountMap,  Decoder<?> decoder ) throws UnknownHostException;
}
