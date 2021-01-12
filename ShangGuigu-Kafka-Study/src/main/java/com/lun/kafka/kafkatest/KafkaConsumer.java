package com.lun.kafka.kafkatest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import kafka.consumer.Consumer;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

/**
 * java实现Kafka消费者的示例
 *
 * @author lisg
 */
public class KafkaConsumer {
    private static String bootstrapServers = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
    private final static String ZK_CONNECT = "xxg.kafka.cn:2181";
    private static final String TOPIC = "test1";
    private static final int THREAD_AMOUNT = 3;
    
    public static void main(String[] args) {
        
        Properties props = new Properties();
        props.put("zookeeper.connect", ZK_CONNECT); // 旧的版本使用这个配置
        // props.put("bootstrap.servers", bootstrapServers);  新版本的kafka使用这个配置
        props.put("group.id", "group2");
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        //每个topic使用多少个kafkastream读取, 多个consumer
        topicCountMap.put(TOPIC, THREAD_AMOUNT);
        //可以读取多个topic
//      topicCountMap.put(TOPIC2, 1);
        ConsumerConnector consumer = Consumer.createJavaConsumerConnector(new ConsumerConfig(props));
        
        Map<String, List<KafkaStream<byte[], byte[]>>> msgStreams = consumer.createMessageStreams(topicCountMap);
        List<KafkaStream<byte[], byte[]>> msgStreamList = msgStreams.get(TOPIC);

//        Executors.newCachedThreadPool();
//        Executors.newScheduledThreadPool(2)
//        Executors.newSingleThreadExecutor();
        //使用ExecutorService来调度线程
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_AMOUNT);
        for (int i = 0; i < msgStreamList.size(); i++) {// 根据Stream的个数，启动多个线程处理数据
            KafkaStream<byte[], byte[]> kafkaStream = msgStreamList.get(i);
            executor.submit(new HanldMessageThread(kafkaStream, i));
        }
        //关闭consumer
        try {
            Thread.sleep(60000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (consumer != null) {
            consumer.shutdown();
        }
        if (executor != null) {
            executor.shutdown();
        }
        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted during shutdown, exiting uncleanly");
        }
    }
    
}
 
