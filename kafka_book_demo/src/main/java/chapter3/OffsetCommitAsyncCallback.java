package chapter3;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by 朱小厮 on 2018/7/29.
 */
@Slf4j
public class OffsetCommitAsyncCallback {
    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo";
    private static AtomicBoolean running = new AtomicBoolean(true);

    public static Properties initConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 默认三个值：latest， none
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }

    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topic));

        try {
            // 第二种
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(1000);
                for (ConsumerRecord<String, String> record : records) {
                    //do some logical processing.
                }
                consumer.commitAsync(new OffsetCommitCallback() {
                    @Override
                    public void onComplete(Map<TopicPartition, OffsetAndMetadata> offsets,
                                           Exception exception) {
                        if (exception == null) {
                            System.out.println(offsets);
                        } else {
                            log.error("fail to commit offsets {}", offsets, exception);
                        }
                    }
                });
            }
        } finally {
            consumer.close();
        }

        // 第一种
        try {
            while (running.get()) {
    
                ConsumerRecords<String, String> records = consumer.poll(1000);
                for (ConsumerRecord<String, String> record : records) {
                    //do some logical processing.
                }
                
                //poll records and do some logical processing.
                consumer.commitAsync();
            }
        } finally {
            try {
                // 再次执行一次同步提交
                consumer.commitSync();  // 这个代码作用：在消费者正常退出时，为位移提交【把关保证】添加的。
            } finally {
                consumer.close();
            }
        }
    }
}
