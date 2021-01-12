package com.lun.kafka.kafkatest;

import java.util.*;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

public class KafkaConsumerTestV1 {
    
    private static KafkaConsumer<String, String> consumer;
    private static Properties kfkProperties;
    private static String bootstrapServers = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
    
    static {
        kfkProperties = new Properties();
        kfkProperties.put("bootstrap.servers", bootstrapServers);
        kfkProperties.put("group.id", "kafkatest");
        kfkProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kfkProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    }
    
    
    public static void main(String[] args) {
        String topicName = "test1";
//        consumerMessageAutoCommit(topicName);
        generalConsumerMessageAutoCommit(topicName);
//        generalConsumerMessageManualCommitSync(topicName);
//
//        generalConsumerMessageManualCommitAsync(topicName);

//        generalConsumerMessageManualCommitAsyncWithCallBack(topicName);
//        generalMixSyncAndAsyncCommit(topicName);
    }
    
    private static void consumerMessageAutoCommit(String topicName) {
        Properties props = new Properties();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("group.id", "test1");
        props.put("enable.auto.commit", "true"); // 自动提交
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(Arrays.asList(topicName));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(100);
            for (ConsumerRecord<String, String> record : records)
                System.out.printf("offset = %d, key = %s, value = %s \n", record.offset(), record.key(), record.value());
        }
    }
    
    /**
     * consumer 1 : 自动提交位移
     */
    private static void generalConsumerMessageAutoCommit(String topicName) {
        kfkProperties.put("enable.auto.commit", true);
        consumer = new KafkaConsumer<String, String>(kfkProperties);
        // consumer.subscribe(Collections.singletonList("kafkatest"));
        List<TopicPartition> lists = new ArrayList<TopicPartition>();
        // 设置topic对应的分区
        TopicPartition partition0 = new TopicPartition(topicName, 0);
        lists.add(partition0);
        TopicPartition partition1 = new TopicPartition(topicName, 1);
        lists.add(partition1);
        TopicPartition partition2 = new TopicPartition(topicName, 2);
        lists.add(partition2);
        
        consumer.assign(lists);
        //通过消费者命令行可以实现，只要在命令行中加上--from-beginning即可（具体可见文章 Kafka安装与配置 ），
        // 但是通过Java客户端代码如何实现呢？这就要用到消息偏移量的重定位方法 seek() 或者直接使用 seekToBeginning() 方法，
        // 基于再均衡监听器，在给消费者分配分区的时候将消息偏移量跳转到起始位置
        consumer.seekToBeginning(lists);// 设置从头开始消息
        consumer.seek(partition0, 0); // 设置这个分区从哪一个offset开始消费
        consumer.seek(partition1, 0); // 设置这个分区从哪一个offset开始消费
        consumer.seek(partition2, 0); // 设置这个分区从哪一个offset开始消费
        try {
            while (true) {
                
                ConsumerRecords<String, String> records = consumer.poll(8000);
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
                }
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
            }
            
        } finally {
            consumer.close();
        }
    }
    
    /**
     * consumer 2 : 手动提交位移
     */
    private static void generalConsumerMessageManualCommitSync(String topicName) {
        kfkProperties.put("enable.auto.commit", false);
        kfkProperties.put("max.poll.records", 10);//设置每次拉取最大10条
        consumer = new KafkaConsumer<>(kfkProperties);
        consumer.subscribe(Collections.singletonList(topicName));
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(80);
            for (ConsumerRecord<String, String> record : records) {
                System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
            }
            try {
                consumer.commitSync();
            } catch (CommitFailedException e) {
                System.out.println("commit failed msg" + e.getMessage());
            }
        }
    }
    
    /**
     * consumer 3 异步提交位移
     */
    private static void generalConsumerMessageManualCommitAsync(String topicName) {
        kfkProperties.put("enable.auto.commit", false);
        kfkProperties.put("max.poll.records", 10);//设置每次拉取最大10条
        consumer = new KafkaConsumer<String, String>(kfkProperties);
        
        consumer.subscribe(Collections.singletonList(topicName));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(80);
            for (ConsumerRecord<String, String> record : records) {
                System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
            }
            System.out.println("-----------------------------------------------------");
            consumer.commitAsync();
        }
    }
    
    /**
     * consumer 4 异步提交位移带回调
     */
    private static void generalConsumerMessageManualCommitAsyncWithCallBack(String topicName) {
        kfkProperties.put("enable.auto.commit", false);
        kfkProperties.put("max.poll.records", 10);//设置每次拉取最大10条
        consumer = new KafkaConsumer<String, String>(kfkProperties);
        consumer.subscribe(Collections.singletonList(topicName));
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(80);
            for (ConsumerRecord<String, String> record : records) {
                System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
            }
            // 每次拉去10条，打印一次
            System.out.println("--------------------4-----------------");
            consumer.commitAsync((offsets, e) -> {
                if (null != e) {
                    System.out.println("commit async callback error" + e.getMessage());
                    System.out.println(offsets);
                }
            });
        }
    }
    
    /**
     * consumer 5 混合提交方式
     */
    private static void generalMixSyncAndAsyncCommit(String topicName) {
        kfkProperties.put("enable.auto.commit", false);
        kfkProperties.put("max.poll.records", 10);//设置每次拉取最大10条
        consumer = new KafkaConsumer<>(kfkProperties);
        consumer.subscribe(Collections.singletonList(topicName));
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(80);
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
                }
                consumer.commitAsync();
            }
        } catch (Exception e) {
            System.out.println("commit async error: " + e.getMessage());
        } finally {
            try {
                consumer.commitSync();
            } finally {
                consumer.close();
            }
        }
    }
}
