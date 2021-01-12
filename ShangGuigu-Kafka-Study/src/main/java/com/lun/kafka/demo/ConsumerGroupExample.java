package com.lun.kafka.demo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

public class ConsumerGroupExample {
    private final static String Broker_List = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
    private final ConsumerConnector consumer;
    private final String topic;
    private ExecutorService executor;
    
    public ConsumerGroupExample(String a_zookeeper, String a_groupId, String a_topic) {
        consumer = Consumer.createJavaConsumerConnector(createConsumerConfig(a_zookeeper, a_groupId));
        this.topic = a_topic;
    }
    
    public void shutdown() {
        if (consumer != null) consumer.shutdown();
        if (executor != null) executor.shutdown();
    }
    
    public void run(int a_numThreads) {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(a_numThreads));
        
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(topic);
        
        // 创建一个线程池
        executor = Executors.newFixedThreadPool(a_numThreads);
        
        // 开始消费消息  
        int threadNumber = 0;
        for (final KafkaStream<byte[], byte[]> stream : streams) {
            // 提交执行每个任务
            executor.submit(new ConsumerService(stream, threadNumber));
            threadNumber++;
        }
    }
    
    private static ConsumerConfig createConsumerConfig(String a_zookeeper, String a_groupId) {
        Properties props = new Properties();
        props.put("bootstrap.servers", Broker_List);
        props.put("zookeeper.connect", a_zookeeper);
        props.put("group.id", a_groupId);
        props.put("zookeeper.session.timeout.ms", "60000");
        props.put("zookeeper.sync.time.ms", "2000");
        props.put("auto.commit.interval.ms", "1000");
        return new ConsumerConfig(props);
    }
    
}  