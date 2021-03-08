package chapter3.demo;

import org.apache.kafka.clients.producer.*;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class ProducerT {
    
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        Properties props = initConfigProperties();
        
        //2 create object of producer
        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);
        //3 producer
        for (int i = 0; i < 10; i++) {
           /* kafkaProducer.send(new ProducerRecord<>("mytp","hello", "hello server java client" + i), (metadata, exception) -> {
                //callback function
                if (metadata != null) {
                    System.out.println("partition" + metadata.partition() + "  offset" + metadata.offset());
                }else {
                    System.out.println(exception.fillInStackTrace());
                }
            });*/
           // 指定分区，指定时间戳，创建发送记录消息
            ProducerRecord<String, String> producerRecord1 =
                    new ProducerRecord<String, String>("mytp", 0, System.currentTimeMillis(), Integer.toString(i), "hello server java client" + i);
            
            ProducerRecord<String, String> producerRecord = new ProducerRecord<String, String>("mytp", Integer.toString(i), "hello server java client" + i);
            RecordMetadata metadata = kafkaProducer.send(producerRecord).get();
            System.out.println("partition:" + metadata.partition() + "  topic" + metadata.topic() + " offset:" + metadata.offset());
            // System.out.println(i);
        }
        // System.out.println("finish");
        //4 flush and process data in function of close()
        kafkaProducer.close();
    }
    
    private static Properties initConfigProperties() {
        //1
        Properties props = new Properties();
        //props.put("bootstrap.servers", "chdp11:9092");
        //using Config Class for convenience
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "chdp11:9092，chdp12:9092，chdp12:9092");
        //set ack response type for all of ISR
        props.put("acks", "all");
        //set retries to connect if faill
        props.put("retries", 3);
        //set size for a batch data
        props.put("batch.size", 16384);
        //set delay
        props.put("linger.ms", 3);
        //set buffer size (The total bytes of memory the producer can use to buffer records waiting to be sent to the server)
        props.put("buffer.memory", 33554432);
        //serializable class
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        
        return props;
    }
}
