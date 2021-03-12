package chapter7.demo;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.requests.IsolationLevel;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 消费Kafka，保证事务性
 *
 * @author jast
 * @date 2020/4/21 22:54
 */
public class TransactionConsumer {
    private static final String KAFKA_BROKERS = "localhost:9092";
    
    /**
     * 事务性kafka消费
     *
     * @param topic
     * @param max_poll_records
     * @param group
     * @return
     */
    public KafkaConsumer<String, String> transactionConsumer(String topic, String group, int max_poll_records, boolean isLatest) {
        Properties props = new Properties();
        //-----------------------------------------------------------------------------------
        //设置只读事务提交成功后的数据
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString().toLowerCase());
        //-----------------------------------------------------------------------------------
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, max_poll_records);//控制每次poll的数量
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);//自动提交 false
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, isLatest == true ? "latest" : "earliest");
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 5 * 1024 * 1024);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(Arrays.asList(topic));
        return consumer;
    }
    
    public KafkaConsumer<String, String> consumer(String topic, String group, int max_poll_records, boolean isLatest) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, max_poll_records);//控制每次poll的数量
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);//自动提交 false
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, isLatest == true ? "latest" : "earliest");
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 5 * 1024 * 1024);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(Arrays.asList(topic));
        return consumer;
    }
    
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        TransactionConsumer transactionConsumer = new TransactionConsumer();
        KafkaConsumer<String, String> consumer = transactionConsumer.consumer("test", "test", 10, false);
        
        // 创建事务性的消费者
        TransactionConsumer transactionConsumer2 = new TransactionConsumer();
        KafkaConsumer<String, String> consumer2 = transactionConsumer2.transactionConsumer("test", "test2", 10, false);
        
        CompletableFuture.runAsync(() -> {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(1000);
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("普通消费者消费数据->" + record.value() + " partition:" + record.partition() + " offset:" + record.offset());
                }
//                System.out.println("普通消费者休眠1秒");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        CompletableFuture.runAsync(() -> {
            while (true) {
                ConsumerRecords<String, String> records2 = consumer2.poll(1000);
                for (ConsumerRecord<String, String> record : records2) {
                    System.out.println("事务消费者消费数据->" + record.value() + " partition:" + record.partition() + " offset:" + record.offset());
                }
//                System.out.println("事务消费者休眠1秒");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).get();
        
    }
}