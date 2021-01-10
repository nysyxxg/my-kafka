package com.lun.kafka.producer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class SyncProducer {
	
	public static void main(String[] args) throws InterruptedException, ExecutionException{
		Properties props = new Properties();
		props.put("bootstrap.servers", "127.0.0.1:9092");//kafka 集群， broker-list
		props.put("acks", "all");
		props.put("retries", 1);//重试次数
		props.put("batch.size", 16384);//批次大小
		props.put("linger.ms", 1);//等待时间
		props.put("buffer.memory", 33554432);//RecordAccumulator 缓冲区大小
		props.put("key.serializer",
		"org.apache.kafka.common.serialization.StringSerializer");
		props.put("value.serializer",
		"org.apache.kafka.common.serialization.StringSerializer");
		
//		props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, MyPartitioner.class);
		
		Producer<String, String> producer = new KafkaProducer<>(props);
		for (int i = 0; i < 100; i++) {
			producer.send(new ProducerRecord<String, String>("test",  "test - 1"), new Callback() {
			
				//回调函数， 该方法会在 Producer 收到 ack 时调用，为异步调用
				@Override
				public void onCompletion(RecordMetadata metadata, Exception exception) {
					if (exception == null) {
						System.out.println(metadata.partition() + " - " + metadata.offset());
					} else {
						exception.printStackTrace();
					}
				}
			}).get();
		}
		
		producer.close();
	}
}
