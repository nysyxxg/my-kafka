package chapter2;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 代码清单2-1
 * Created by 朱小厮 on 2018/8/29.
 */
public class KafkaProducerAnalysis {
    public static final String brokerList = "xxg.kafka.cn:9095";
//    public static final String brokerList = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
    public static final String topic = "topic-demo";
    
    public static Properties initConfig() {
        Properties props = new Properties();
        props.put("bootstrap.servers", brokerList);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("client.id", "producer.client.id.demo");
        return props;
    }
    
    public static Properties initNewConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "producer.client.id.demo");
        return props;
    }
    
    // 进一步改进
    public static Properties initPerferConfig() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.RETRIES_CONFIG,10);// 设置重试次数
        return props;
    }
    
    public static void main(String[] args) throws InterruptedException {
        Properties props = initConfig();
//        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        KafkaProducer<String, String> producer = new KafkaProducer<>(props,new StringSerializer(), new StringSerializer());
        
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, "hello, Kafka!");
        try {
            // 第一种方式: 同步发送
//            Future<RecordMetadata> future = producer.send(record);
//            future.get();
            
            // 第二种方式 ： 异步发送
            producer.send(record, new Callback() { // 发送消息之后，进行回调-返回记录的元数据信息
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception == null) {
                        System.out.println(metadata.topic() + ":" + metadata.partition() + ":" + metadata.offset() + ":");
                    }else if(exception != null){
                        // 说明存在异常
                        exception.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
        producer.close();
//        TimeUnit.SECONDS.sleep(5);
    }
}
