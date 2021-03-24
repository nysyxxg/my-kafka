package kafka.consumer;

import java.util.*;

import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

public class KafkaConsumerTestV1 {
    
    private static KafkaConsumer<String, String> consumer;
    private static Properties kfkProperties;
    
    static {
        kfkProperties = new Properties();
        kfkProperties.put("bootstrap.servers", "xxg.kafka.cn:9092");
        kfkProperties.put("group.id", "kafkatest");
        kfkProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        kfkProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        
    }
    
    
    public static void main(String[] args) {
        String topicName = "test_topic";
        generalConsumerMessage(topicName);
        generalConsumerMessageAutoCommit(topicName);
    }
    
    private static void generalConsumerMessage(String topicName) {
        
        kfkProperties.put("enable.auto.commit", "true");
        kfkProperties.put("auto.commit.interval.ms", "1000");
        kfkProperties.put("session.timeout.ms", "30000");
        
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(kfkProperties);
        consumer.subscribe(Arrays.asList(topicName));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(100);
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("offset = %d, key = %s, value = %s \n", record.offset(), record.key(), record.value());
            }
        }
    }
    
    
    /**
     * consumer 1 : 自动提交位移
     */
    private static void generalConsumerMessageAutoCommit(String topicName) {
        kfkProperties.put("enable.auto.commit", true);
//        consumer = new KafkaConsumer<String, String>(kfkProperties);
        consumer.subscribe(Collections.singletonList("kafkatest"));
        TopicPartition partition = new TopicPartition(topicName, 1);
        List<TopicPartition> lists = new ArrayList<TopicPartition>();
        lists.add(partition);
        consumer.assign(lists);
        consumer.seekToBeginning(lists);

// consumer.seek(partition, 0);
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
    private static void generalConsumerMessageManualCommitSync() {
        kfkProperties.put("enable.auto.commit", false);
        consumer = new KafkaConsumer<>(kfkProperties);
        consumer.subscribe(Collections.singletonList("kafkatest"));
        
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
    private static void generalConsumerMessageManualCommitAsync() {
        kfkProperties.put("enable.auto.commit", false);
        consumer = new KafkaConsumer<String, String>(kfkProperties);
        consumer.subscribe(Collections.singletonList("kafkatest"));
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(80);
            for (ConsumerRecord<String, String> record : records) {
                System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
                
            }
            
            consumer.commitAsync();
        }
    }
    
    /**
     * consumer 4 异步提交位移带回调
     */
    private static void generalConsumerMessageManualCommitAsyncWithCallBack() {
        kfkProperties.put("enable.auto.commit", false);
        consumer = new KafkaConsumer<String, String>(kfkProperties);
        consumer.subscribe(Collections.singletonList("kafkatest"));
        
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(80);
            for (ConsumerRecord<String, String> record : records) {
                System.out.println(record.timestamp() + "," + record.topic() + "," + record.partition() + "," + record.offset() + " " + record.key() + "," + record.value());
            }
            
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
    private static void generalMixSyncAndAsyncCommit() {
        kfkProperties.put("enable.auto.commit", false);
        consumer = new KafkaConsumer<>(kfkProperties);
        consumer.subscribe(Collections.singletonList("kafkatest"));
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
