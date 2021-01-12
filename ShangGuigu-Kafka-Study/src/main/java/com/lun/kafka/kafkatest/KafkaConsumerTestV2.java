package com.lun.kafka.kafkatest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

public class KafkaConsumerTestV2 implements Runnable {
    private static ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaConsumer<String, String> consumer;
    private ConsumerRecords<String, String> msgList;
    
    private String topic = "test1";
    private static final String GROUPID = "groupB";
    
    public KafkaConsumerTestV2(String topicName) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093");
        props.put("group.id", GROUPID);
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("session.timeout.ms", "30000");
        props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        this.consumer = new KafkaConsumer<String, String>(props);
        this.topic = topicName;
        this.consumer.subscribe(Arrays.asList(topic));
    }
    
    @Override
    public void run() {
        int messageNo = 1;
        System.out.println("---------开始消费---------");
        try {
            for (; ; ) {
                msgList = consumer.poll(1000);
                if (null != msgList && msgList.count() > 0) {
                    for (ConsumerRecord<String, String> record : msgList) {
                        int partiton = record.partition();
                        //消费100条就打印 ,但打印的数据不一定是这个规律的
//                        if (messageNo % 100 == 0) {
                        
                        System.out.println(messageNo + "=======receive: key = " + record.key() + ", value = " + record.value() + " offset===" + record.offset() + "  partiton=" + partiton);
//                        }
//                        当消费了1000条就退出
                        if (messageNo % 1000 == 0) {
                            break;
                        }
                        messageNo++;
                        System.out.println(messageNo);
                    }
                } else {
                    Thread.sleep(1000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            consumer.close();
        }
    }
    
    public static void main(String args[]) {
        String topic = "test1";
        KafkaConsumerTestV2 test1 = new KafkaConsumerTestV2(topic);
        Thread thread1 = new Thread(test1);
        thread1.start();
    }
}