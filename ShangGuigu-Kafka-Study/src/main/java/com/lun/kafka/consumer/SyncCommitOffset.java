package com.lun.kafka.consumer;

import java.util.Arrays;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public class SyncCommitOffset {
	public static void main(String[] args) {
		Properties props = new Properties();
		//Kafka 集群
		props.put("bootstrap.servers", "hadoop102:9092");
		//消费者组，只要 group.id 相同，就属于同一个消费者组
		props.put("group.id", "test");
		props.put("enable.auto.commit", "false");//关闭自动提交 offset
		props.put("key.deserializer",
				"org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer",
				"org.apache.kafka.common.serialization.StringDeserializer");
		KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
		consumer.subscribe(Arrays.asList("first"));//消费者订阅主题
		while (true) {
			//消费者拉取数据
			ConsumerRecords<String, String> records =
			consumer.poll(100);
			for (ConsumerRecord<String, String> record : records) {
				System.out.printf("offset = %d, key = %s, value= %s%n", record.offset(), record.key(), record.value());
			}
			//同步提交，当前线程会阻塞直到 offset 提交成功
			consumer.commitSync();
		}
	}
}
