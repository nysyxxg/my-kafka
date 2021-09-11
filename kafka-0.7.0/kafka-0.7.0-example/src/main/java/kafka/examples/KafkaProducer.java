package kafka.examples;


import kafka.javaapi.producer.ProducerData;
import kafka.producer.ProducerConfig;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class KafkaProducer {
    
    public static void main(String[] args) {
        
        String topic = "test-xxg";
        
        String pathFile = "D:\\my-kafka\\kafka-0.7.0\\data\\test1.data";
        if (args.length == 1) {
            pathFile = args[0];
        }
        
        Properties props = new Properties();
        // kafka 集群， broker-list
//        props.put("bootstrap.servers", bootstrapServers);
        props.put("zk.connect", "localhost:2181");
        //可用ProducerConfig.ACKS_CONFIG 代替 "acks"
        //props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put("acks", "all");
        // 重试次数
        props.put("retries", "1");
        // 批次大小
        props.put("batch.size", "16384");
        // 等待时间
        props.put("linger.ms", "1");
        // RecordAccumulator 缓冲区大小
        props.put("buffer.memory", "33554432");
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        kafka.javaapi.producer.Producer<Integer, String> producer =
                new kafka.javaapi.producer.Producer<Integer, String>(new ProducerConfig(props));
//        for (int i = 1; i < 10; i++) {
//            producer.send(new ProducerRecord<String, String>(topic, "test-" + Integer.toString(i), "test-" + Integer.toString(i)));
//        }
        try {
            FileReader fr = new FileReader(pathFile);
            BufferedReader br = new BufferedReader(fr);
            String line = "";
            while ((line = br.readLine()) != null) {
                System.out.println(line);
                producer.send(new ProducerData<Integer, String>(topic, line));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
        producer.close();
    }
    
}
