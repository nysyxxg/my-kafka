package com.lun.kafka.producer;

import java.util.Properties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.lun.kafka.util.DataUtil;
import com.lun.kafka.vo.DataVo;
import com.lun.kafka.vo.People;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class CustomProducer {
    
    public static void main(String[] args) {
        
        String topic = "test-json";
        String bootstrapServers = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
        
        Properties props = new Properties();
        // kafka 集群， broker-list
        props.put("bootstrap.servers", bootstrapServers);
        
        //可用ProducerConfig.ACKS_CONFIG 代替 "acks"
        //props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put("acks", "all");
        // 重试次数
        props.put("retries", 1);
        // 批次大小
        props.put("batch.size", 16384);
        // 等待时间
        props.put("linger.ms", 1);
        // RecordAccumulator 缓冲区大小
        props.put("buffer.memory", 33554432);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> producer = new KafkaProducer<>(props);
//        for (int i = 400; i < 500; i++) {
//            producer.send(new ProducerRecord<String, String>(topic, "test-" + Integer.toString(i), "test-" + Integer.toString(i)));
//        }
        
        for (int i = 1; i < 2; i++) {
            People people = DataUtil.getPeople();
            DataVo dataVo = DataUtil.getDataVo(people);
            dataVo.setDbType("mysql");
            String jsondata = null;
            try {
                jsondata = DataUtil.getJsonData(dataVo);
                System.out.println("发送数据：" + jsondata);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            producer.send(new ProducerRecord<String, String>(topic, "test-" + Integer.toString(i), jsondata));
        }
        
        producer.close();
    }
    
}
