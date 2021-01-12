package com.lun.kafka.consumer;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.OffsetCommitCallback;
import org.apache.kafka.common.TopicPartition;

public class AsyncCommitOffset {
    public static void main(String[] args) {
        Properties props = new Properties();
        // Kafka 集群
        props.put("bootstrap.servers", "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093");
        // 消费者组，只要 group.id 相同，就属于同一个消费者组
        props.put("group.id", "test1");
        
        // 关闭自动提交 offset
        props.put("enable.auto.commit", "false");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("test1"));// 消费者订阅主题
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(100);// 消费者拉取数据
            for (ConsumerRecord<String, String> record : records) {
                System.out.printf("offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());
            }
            // 异步提交
            consumer.commitAsync(new OffsetCommitCallback() {
                @Override
                public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets, Exception exception) {
                    if (exception != null) {
                        System.err.println("Commit failed for" + offsets);
                    } else {
                        for (TopicPartition topicPartition : offsets.keySet()) {
                            OffsetAndMetadata offsetAndMetadata = offsets.get(topicPartition);
                            String msg = "metadata " + offsetAndMetadata.metadata() + " offset=" + offsetAndMetadata.offset() + " topic=" + topicPartition.topic() + " partition=" + topicPartition.partition();
                            System.out.println(msg);
                        }
                    }
                }
            });
        }
    }
}
