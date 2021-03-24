package kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 新的版本，需要使用 bootstrap.servers 进行连接。
 * java实现Kafka消费者的示例
 */
public class KafkaConsumerTestV0 {
    private static final String topicName = "test1";
    private static final int THREAD_AMOUNT = 2;
    
    public static void main(String[] args) {
        ConcurrentHashMap<String, List<Long>> dataMap = new ConcurrentHashMap<>();
        Properties props = new Properties();
        props.put("bootstrap.servers", "xxg.kafka.cn:9092");
        props.put("group.id", "group2");
        props.put("auto.offset.reset", "earliest"); // 如果更换一个消费组，需要设置这个参数，才能从头开始消费，默认从最新的开始消费
        props.put("zookeeper.session.timeout.ms", "400");
        props.put("zookeeper.sync.time.ms", "200");
        props.put("auto.commit.interval.ms", "1000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
        consumer.subscribe(Arrays.asList(topicName));
        
        //使用ExecutorService来调度线程
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_AMOUNT);
        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(100);
                if (!records.isEmpty()) {
                    executor.submit(new HanldMessageThreadV0(records, dataMap));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //关闭consumer
            if (consumer != null) {
                consumer.close();
            }
            if (executor != null) {
                executor.shutdown();
            }
        }
        try {
            if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted during shutdown, exiting uncleanly");
        }
    }
    
}

/**
 * 具体处理message的线程
 */
class HanldMessageThreadV0 implements Runnable {
    private ConcurrentHashMap<String, List<Long>> dataMap;
    private ConsumerRecords<String, String> records = null;
    private int num = 0;
    
    public HanldMessageThreadV0(ConsumerRecords<String, String> records, ConcurrentHashMap<String, List<Long>> dataMap) {
        super();
        this.records = records;
        this.dataMap = dataMap;
    }
    
    public void run() {
        for (ConsumerRecord<String, String> record : records)
            System.out.println("Thread no: " + Thread.currentThread().getName() + ",   message: " + record.value());
    }
}