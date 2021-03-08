package chapter3.thread;


import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 代码清单3-12
 * Created by 朱小厮 on 2018/8/25.
 */
public class ThirdMultiConsumerThreadDemo {
    public static final String brokerList = "localhost:9092";
    public static final String topic = "topic-demo";
    public static final String groupId = "group.demo";
    
    public static Properties initConfig() {
        Properties props = new Properties();
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        
        //自动位移提交
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        
        return props;
    }
    
    public static void main(String[] args) {
        Properties props = initConfig();
        KafkaConsumerThread consumerThread = new KafkaConsumerThread(props, topic, Runtime.getRuntime().availableProcessors());
        consumerThread.start();
    }
    
    public static class KafkaConsumerThread extends Thread {
        private KafkaConsumer<String, String> kafkaConsumer;
        private ExecutorService executorService;
        private int threadNumber;
        
        public KafkaConsumerThread(Properties props, String topic, int threadNumber) {
            kafkaConsumer = new KafkaConsumer<>(props);
            kafkaConsumer.subscribe(Collections.singletonList(topic));
            this.threadNumber = threadNumber;
            // 初始化线程池
            executorService = new ThreadPoolExecutor(threadNumber, threadNumber,
                    0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000),
                    new ThreadPoolExecutor.CallerRunsPolicy()); // 注意这个设置
            // CallerRunsPolicy 这个参数设置，可以防止线程池的总体消费能力跟不上poll拉取的能力，从而导致异常现象的发生。
            // 这种方法可以横向扩展，可以开启多个KafkaConsumerThread实例，来进一步提升整体的消费能力。
        }
        
        @Override
        public void run() {
            try {
                while (true) {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(100));
                    if (!records.isEmpty()) {
                        //没获取一批数据，  就提交一个线程去处理消息
                        executorService.submit(new RecordsHandler(kafkaConsumer,records));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                kafkaConsumer.close();
            }
        }
        
    }
    
    public static class RecordsHandler extends Thread {
        private KafkaConsumer<String, String> kafkaConsumer;
        public final ConsumerRecords<String, String> records;
        
        public RecordsHandler(KafkaConsumer kafkaConsumer , ConsumerRecords<String, String> records) {
            this.kafkaConsumer = kafkaConsumer;
            this.records = records;
        }
        
        @Override
        public void run() {
            //处理records. 当出现多个线程进行处理的时候，这里会出现线程安全问题
            // 在实现的过程中，对offsets的读写需要加锁处理，防止出现并发问题。
//            for (ConsumerRecord<String, String> record : records) {
//                System.out.println(record.value());
//            }
            Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap();
            // 下面手动提交offset。防止并发问题
            for (TopicPartition tp : records.partitions()) {
                List<ConsumerRecord<String, String>> tpRecords = records.records(tp);
                // 处理 tpRecords
                for(ConsumerRecord<String, String>  record:tpRecords){
                    System.out.println(record.value()); // 处理数据
                }
                
                long lastConsumedOffset = tpRecords.get(tpRecords.size() - 1).offset();
                synchronized (offsets) {
                    if (offsets.containsKey(tp)) {
                        offsets.put(tp, new OffsetAndMetadata(lastConsumedOffset + 1));// 缓存offset
                    } else {
                        long position = offsets.get(tp).offset();
                        if (position < lastConsumedOffset + 1) {
                            offsets.put(tp, new OffsetAndMetadata(lastConsumedOffset + 1));
                            
                        }
                    }
                }
            }
            synchronized (offsets){ // 开始提交offset
                if(!offsets.isEmpty()){
                    kafkaConsumer.commitSync(offsets);
                    offsets.clear();
                }
            }
            
            // 上面的问题：对于同一个分区数据，如果t1一个线程开始处理0-99的数据，t2一个线程处理100-199的数据，
            // 假设其中t1 消费线程，出现了异常。之后的消费数据，也会从100-开始进行消费，并且无法消费0-99的数据。从而造成数据丢失
        }
    }
}
