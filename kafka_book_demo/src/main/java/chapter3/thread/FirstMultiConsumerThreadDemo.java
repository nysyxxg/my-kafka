package chapter3.thread;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

/**
 * 代码清单 3-11
 * Created by 朱小厮 on 2018/8/25.
 */
public class FirstMultiConsumerThreadDemo {
    public static final String brokerList = "xxg.kafka.cn:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo.test3";
    
    public static Properties initConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }
    
    public static void main(String[] args) {
        Properties props = initConfig();
        int consumerThreadNum = 4; //一般设置为topic的分区个数，一般一个主题的分区数事先可以知晓，不能设置成大于分区数的值
        // 如果不知道topic的分区个数，可以设置
        //kafkaConsumer.partitionsFor(topic); 这个方法可以获取topic分区个数
        for (int i = 0; i < consumerThreadNum; i++) {
            new KafkaConsumerThread(props, topic).start();
        }
    }
    
    public static class KafkaConsumerThread extends Thread {
        // Kafka的Producer是线程安全的
        // KafkaConsumer 不是线程安全的
        private KafkaConsumer<String, String> kafkaConsumer;
        
        public KafkaConsumerThread(Properties props, String topic) {
            this.kafkaConsumer = new KafkaConsumer<>(props);
            
            this.kafkaConsumer.subscribe(Arrays.asList(topic));
        }
        
        @Override
        public void run() {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records) {
                        //process record.
                        System.out.println("线程名称：" + Thread.currentThread().getName() + "分区名称：" + record.partition() + "----->" + record.value());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                kafkaConsumer.close();
            }
        }
    }
}
