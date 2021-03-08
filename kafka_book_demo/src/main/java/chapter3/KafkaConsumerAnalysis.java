package chapter3;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 代码清单3-1
 * Created by 朱小厮 on 2018/7/22.
 */
@Slf4j
public class KafkaConsumerAnalysis {
    
    public static final String brokerList = "xxg.kafka.cn:9092";
    public static final String topic1 = "topic1";
    public static final String topic2 = "topic2";
    public static final String topic3 = "topic3";
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
        List<String> topicList = Arrays.asList(topic1, topic2, topic3);
        consumer.subscribe(topicList);
        // 如果下面写多个，后面的订阅会覆盖前面的订阅
//        consumer.subscribe(Arrays.asList(topic1));
//        consumer.subscribe(Arrays.asList(topic2));
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
                // kafka消费是不断轮询的方案，poll 有一个超时时间参数timeout，用来控制poll方法的阻塞时间，
                // 在消费者的缓冲区中没有可用的数据，就会发生阻塞
                // 如果应用线程唯一的工作就是从kafka中拉取并消费消息，则可以将这个参数设置为最大值，//
                // 如果timeout设置为0 ，这样poll方法会立即执行返回，而不管是否拉取到了消息
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
//                records.records(TopicPartition partition)
//                records.partitions()
                //第一种：  获取消息的所有分区信息
                Set<TopicPartition> topicPartitionSet = records.partitions();
                for (TopicPartition topicPartition : topicPartitionSet) {// 开始遍历每一分区
                    // 按照主题的维度进行消除数据
                    List<ConsumerRecord<String, String>> consumerRecordList = records.records(topicPartition);// 获取每个分区的消息
                    for (ConsumerRecord<String, String> consumerRecord : consumerRecordList) {
                        System.out.println(consumerRecord.key() + "--------->" + consumerRecord.value());
                    }
                }
                // 第二种：
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("topic = " + record.topic() + ", partition = " + record.partition() + ", offset = " + record.offset());
                    System.out.println("key = " + record.key() + ", value = " + record.value());
                    //do something to process record.
                }
                
                // 第三种：
                for (String topic:topicList) {
                    for (ConsumerRecord<String, String> record : records.records(topic)) {
                        System.out.println(record.topic() + ":" + record.value());
                    }
                }
            }
        } catch (Exception e) {
            log.error("occur exception ", e);
        } finally {
            consumer.close();
        }
    }
}
