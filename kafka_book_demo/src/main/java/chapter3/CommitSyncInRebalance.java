package chapter3;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 代码清单3-9：
 * 消费组的再平衡： topic的分区，从一个消费者转移到另外一个消费者。
 * Created by 朱小厮 on 2018/8/19.
 */
public class CommitSyncInRebalance {
    public static final String brokerList = "xxg.kafka.cn:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo";
    public static final AtomicBoolean isRunning = new AtomicBoolean(true);
    
    public static Properties initConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        return props;
    }
    
    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        
        Map<TopicPartition, OffsetAndMetadata> currentOffsets = new HashMap<>();
        
        // ConsumerRebalanceListener 消费组在均衡监听器。再均衡监听器用来设定发生再均衡动作前后的一些准备，或者收尾的动作。
        consumer.subscribe(Arrays.asList(topic), new ConsumerRebalanceListener() {
            // 这个方法会在再均衡开始之前和消费者停止读取消息之后被调用。可以通过这个回调方法来处理消费位移的提交，
            // 以此来避免一些不必要的重复消费现象和发生。参数partitions 表示在均衡前所分配到的分区
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                for(TopicPartition topicPartition:partitions){
                    System.out.println("----" + topicPartition.partition() + "----"+ topicPartition.topic() + "----");
                }
                System.out.println("-------------onPartitionsRevoked---------------");
                // 在再均衡开始之前和消费者停止读取消息之后被调用。同步提交消费位移
                consumer.commitSync(currentOffsets);
            }
            
            // ：这个方法在平衡之后、消费者开始拉去消息之前被调用，一般在该方法中保证各消费者回滚到正确的偏移量，
            // 即重置各消费者消费偏移量
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                //do nothing.
                System.out.println("-------------onPartitionsAssigned---------------");
                for(TopicPartition topicPartition:partitions){
                    System.out.println("----" + topicPartition.partition() + "----"+ topicPartition.topic() + "----");
                }
            }
        });
        
        try {
            while (isRunning.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, String> record : records) {
                    //process the record.
                    TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
                    OffsetAndMetadata offsetAndMetadata = new OffsetAndMetadata(record.offset() + 1);
                    currentOffsets.put(topicPartition, offsetAndMetadata);// 将消费的位移暂存到一个局部变量currentOffsets中
                }
                consumer.commitAsync(currentOffsets, null);// 在正常消费的时候，可以通过异步提交位移，
            }
        } finally {
            consumer.close();
        }
    }
}
