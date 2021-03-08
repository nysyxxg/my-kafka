package chapter3.seek;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;

/**
 * 代码清单3-7
 * Created by 朱小厮 on 2018/8/19.
 */
public class SeekToEnd {
    public static final String brokerList = "xxg.kafka.cn:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo";
    
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
        Set<TopicPartition> assignment = new HashSet<>();
        while (assignment.size() == 0) {
            consumer.poll(Duration.ofMillis(100));
            assignment = consumer.assignment();
        }

//        consumer.seekToBeginning(Collection<TopicPartition> partitions);// 定位到分区的开头
//        consumer.seek();
//        consumer.seekToEnd(Collection<TopicPartition> partitions);  // 定位到分区的结尾
        //使用seek 方法，从分区末尾消费
        Map<TopicPartition, Long> offsets = consumer.endOffsets(assignment);
        // 含有阻塞方法的
//        Map<TopicPartition, Long> offsets = consumer.endOffsets(assignment,Duration.ofMillis(6*1000));
        
        // 对每个分区设置一个时间，然后根据时间进行消费
        //consumer.offsetsForTimes(Map<TopicPartition, Long> var1)
        //***********************************************************
//        Map<TopicPartition, Long> timestampToSearch = new HashMap<>();
//        for (TopicPartition tp : assignment) {
//            // 设置当前时间，减一天
//            timestampToSearch.put(tp, System.currentTimeMillis() - 1 * 24 * 3600 * 1000);
//        }
//        // 根据时间戳，获取对应的分区offset信息
//        Map<TopicPartition, OffsetAndTimestamp> offsets1 = consumer.offsetsForTimes(timestampToSearch);
//        for (TopicPartition tp : assignment) {
//            OffsetAndTimestamp offsetAndTimestamp = offsets1.get(tp);
//            if (offsetAndTimestamp != null){
//                consumer.seek(tp, offsetAndTimestamp.offset());// 根据时间定位offset的位置
//            }
//        }
        //***********************************************************
        
        for (TopicPartition tp : assignment) {
//          consumer.seek(tp, offsets.get(tp));
            long offset = offsets.get(tp);
            System.out.println("-------offset---------------" + offset);// 上一次消费最后的offset位置
            consumer.seek(tp, offset + 1); // 可以造成offset越界，如果设置的offset超过LEO
            // 日志信息：
            /**
             *    [2021-03-08 16:00:55,263] INFO [Consumer clientId=consumer-1, groupId=group.demo]
             *    Fetch offset 202 is out of range for partition topic-demo-0, resetting offset (org.apache.kafka.clients.consumer.internals.Fetcher:967)
             *    [2021-03-08 16:00:55,296] INFO [Consumer clientId=consumer-1, groupId=group.demo]
             *    Resetting offset for partition topic-demo-0 to offset 201. (org.apache.kafka.clients.consumer.internals.Fetcher:583)
             *   上面的设置，超过了offset最大值，已经越界了，所以会根据auto.offset.reset 重新设置offset
             */
        }
        System.out.println(assignment);
        System.out.println(offsets);
        int index = 0;
        while (true) {// 死循环，去poll消息
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2 * 1000));// 阻塞多久去拉取一次消息
            System.out.println("---------------index-----------" + (index++));
            //consume the record.
            for (ConsumerRecord<String, String> record : records) {
                System.out.println(record.offset() + ":" + record.value());
            }
        }
        
    }
}
