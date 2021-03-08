package chapter3;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 代码清单3-2
 * Created by 朱小厮 on 2018/7/28.
 */
public class CheckOffsetAndPosition {
    public static final String brokerList = "xxg.kafka.cn:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo";
    private static AtomicBoolean running = new AtomicBoolean(true);

    public static Properties initConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
    
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,false);
        
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);

        TopicPartition tp = new TopicPartition(topic, 0);
        consumer.assign(Arrays.asList(tp));
        long lastConsumedOffset = -1;
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(1000);
            if (records.isEmpty()) {//判断是否已经消费完这个分区的消息
                System.out.println("-----退出-----");
                break;
            }
            List<ConsumerRecord<String, String>> partitionRecords = records.records(tp);
            for(ConsumerRecord<String, String> consumerRecord:partitionRecords){// 开始处理消息
                //处理消费数据的逻辑
            }
    
            ConsumerRecord<String, String> record =  partitionRecords.get(partitionRecords.size() - 1);
            lastConsumedOffset = record.offset(); // 获取最后消费记录的位移
            consumer.commitSync();//同步提交消费位移
    
            consumer.commitAsync();// 异步提交
            OffsetCommitCallback offsetCommitCallback = new OffsetCommitCallback(){
                @Override
                public void onComplete(Map<TopicPartition, OffsetAndMetadata> map, Exception e) {
                }
            };
            consumer.commitAsync(offsetCommitCallback);// 异步提交，回调函数
        }
        System.out.println("comsumed offset is " + lastConsumedOffset); // 消息消息最后的offset
        OffsetAndMetadata offsetAndMetadata = consumer.committed(tp);
        System.out.println("commited offset is " + offsetAndMetadata.offset()); // 最后提交的offset
        
        long posititon = consumer.position(tp);
        System.out.println("the offset of the next record is " + posititon);
    }
}
