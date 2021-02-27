package chapter3;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 代码清单3-1
 * Created by 朱小厮 on 2018/7/22.
 */
@Slf4j
public class KafkaConsumerAnalysis {
    
    public static final String brokerList = "xxg.kafka.cn:9095";
    public static final String topic = "topic1";
    public static final String groupId = "group.demo";
    public static final AtomicBoolean isRunning = new AtomicBoolean(true);
    
    public static Properties initConfig() {
        
        Properties props = new Properties();
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("bootstrap.servers", brokerList);
        props.put("group.id", groupId);
        props.put("client.id", "consumer.client.id.demo");
        return props;
    }
    
    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topic));
        // 第一种
//        void subscribe(Collection<String> var1);
//        void subscribe(Collection<String> var1, ConsumerRebalanceListener var2);
        // 第二种
//        void subscribe(Pattern var1, ConsumerRebalanceListener var2);
//        void subscribe(Pattern var1);
        // 第三种
//        void assign(Collection< TopicPartition > partitions)
        // 说明  subscribe() 这种方式订阅主题具有消费者自动在均衡的功能，在多个消费者的情况下可以根据
        // 分区策略来自动分配各个消费者与分区的关系。
        try {
            while (isRunning.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("topic = " + record.topic() + ", partition = " + record.partition() + ", offset = " + record.offset());
                    System.out.println("key = " + record.key() + ", value = " + record.value());
                    //do something to process record.
                }
            }
        } catch (Exception e) {
            log.error("occur exception ", e);
        } finally {
            consumer.close();
        }
    }
}
