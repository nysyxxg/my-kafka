package chapter3.seek;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 代码清单3-8
 * Created by 朱小厮 on 2019-02-27.
 */

public class SeekToDB {
    
    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo1";
    
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
        consumer.subscribe(Arrays.asList(topic));
        
        Set<TopicPartition> assignment = consumer.assignment();
        System.out.println(assignment);
        
        //省略poll()方法以及assignment的逻辑
        for (TopicPartition tp : assignment) {
            long offset = getOffsetFromDB(tp);//从DB中读取消费位移
            consumer.seek(tp, offset); // 指定到指定offset位置
        }
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
            for (TopicPartition partition : records.partitions()) {
                List<ConsumerRecord<String, String>> partitionRecords = records.records(partition);
                for (ConsumerRecord<String, String> record : partitionRecords) {
                    //process the record.
                }
                long lastConsumedOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
                //将消费位移存储在DB中
                storeOffsetToDB(partition, lastConsumedOffset + 1);// 最后保存的offset要加1
            }
        }
    }
    
    private static void storeOffsetToDB(TopicPartition partition, long lastConsumedOffset) {
        // 保存到一个地方：数据库，redis，或者zk中
    
    }
    
    private static long getOffsetFromDB(TopicPartition tp) {
        long offset = 0;
     
        // 根据topic的信息，去数据库中查询已经存入的offset
        return offset;
    }
}