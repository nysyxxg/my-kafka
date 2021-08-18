package kafka.javaapi.consumer;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaMessageStream;
import kafka.message.Message;
import kafka.serializer.Decoder;
import kafka.serializer.DefaultDecoder;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZookeeperConsumerConnector implements ConsumerConnector {
    
    public kafka.consumer.ZookeeperConsumerConnector underlying;
    
    public ZookeeperConsumerConnector(ConsumerConfig config, Boolean enableFetcher) {
        super();
        //  初始化消费端连接器
        this.underlying = new kafka.consumer.ZookeeperConsumerConnector(config, enableFetcher);
    }
    
    public ZookeeperConsumerConnector(ConsumerConfig config) {
        this(config, true);
    }
    
    @Override
    public <T> Map<String, List<KafkaMessageStream<T>>> createMessageStreams(Map<String, Integer> topicCountMap, Decoder<T> decoder) throws UnknownHostException {
        System.out.println("------------------------createMessageStreams--------");
        // 核心消息方法
        Map<String, List<KafkaMessageStream<T>>> scalaReturn = underlying.consume(topicCountMap, decoder);
        Map<String, List<KafkaMessageStream<T>>> ret = new HashMap<String, List<KafkaMessageStream<T>>>();  // 一个topic对应一个List集合
        for (String topic : scalaReturn.keySet()) {
            List<KafkaMessageStream<T>> streams = scalaReturn.get(topic);
            List<KafkaMessageStream<T>> javaStreamList = new java.util.ArrayList<KafkaMessageStream<T>>();
            for (KafkaMessageStream<T> stream : streams) {
                javaStreamList.add(stream);
            }
            ret.put(topic, javaStreamList);
        }
        return ret;
    }
    
    @Override
    public Map<String, List<KafkaMessageStream<Message>>> createMessageStreams(Map<String, Integer> topicCountMap)  {
        try {
            return createMessageStreams(topicCountMap, new DefaultDecoder());
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    @Override
    public void commitOffsets() {
        underlying.commitOffsets();
    }
    
    @Override
    public void shutdown() {
        underlying.shutdown();
    }
}
