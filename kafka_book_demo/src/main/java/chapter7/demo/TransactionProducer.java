package chapter7.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * 事务
 * 事务作用范围
 *
 * 实现方法
 * 通过设置 Producer设置
 * //设置Producer幂等性,其他不用变化
 * props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true);
 *
 * //设置事务，同时也要指定幂等性，自定义id名称
 * props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,"xxg-acid");
 * -------------------------------------------------------------------
 *
 * Consumer设置
 *
 * //设置只读事务提交成功后的数据
 *   props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, IsolationLevel.READ_COMMITTED.toString().toLowerCase())
 * -------------------------------------------------------------------
 * Kafka事务提交，保证exactly once producer
 * 要么全部成功，要么全部失败
 *
 * @date 2020/4/21 22:38
 */
public class TransactionProducer {
    
    private static final String KAFKA_BROKERS = "localhost:9092";
    private static Producer<String, String> producer;
    
    public TransactionProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_BROKERS);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024 * 1024 * 1024);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        
        //设置Producer幂等性,其他不用变化
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        //设置事务，同时也要指定幂等性，自定义id名称
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "xxg-acid");
        producer = new KafkaProducer<String, String>(props);
        
    }
    
    public Producer<String, String> getProducer() {
        return producer;
    }
    
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        
        TransactionProducer transactionProducer = new TransactionProducer();
        Producer<String, String> producer = transactionProducer.getProducer();
        //初始化事务
        producer.initTransactions();
        boolean flag = true;
        //循环四次，最后一次我们把事务成功提交
        //理想结果：前三次事务提交失败
        //  事务消费者消费不到数据 1,2，第四次可以消费到 1,2,3,4；
        //  普通消费者可以消费到前三次的1,2 ,也可以消费到第四次1,2,3,4
        // 运行方法 TransactionConsumer
        /**
         * 结果如下，事务提交成功
         * 普通消费者消费数据->1 partition:2 offset:3080713
         * 事务消费者消费数据->3 partition:2 offset:3080717
         * 普通消费者消费数据->2 partition:1 offset:3081410
         * 普通消费者消费数据->1 partition:3 offset:3081465
         * 普通消费者消费数据->1 partition:2 offset:3080715
         * 普通消费者消费数据->3 partition:2 offset:3080717
         * 事务消费者消费数据->4 partition:1 offset:3081414
         * 事务消费者消费数据->2 partition:0 offset:3081470
         * 事务消费者消费数据->1 partition:3 offset:3081467
         * 普通消费者消费数据->2 partition:1 offset:3081412
         * 普通消费者消费数据->4 partition:1 offset:3081414
         * 普通消费者消费数据->2 partition:0 offset:3081468
         * 普通消费者消费数据->2 partition:0 offset:3081470
         * 普通消费者消费数据->1 partition:3 offset:3081467
         */
        for (int i = 0; i <= 3; i++) {
            if (i == 3)
                flag = false;
            try {
                //事务开始
                producer.beginTransaction();
                producer.send(new ProducerRecord<String, String>("test", "1")).get();
                producer.send(new ProducerRecord<String, String>("test", "2")).get();
                //手动制造异常
                if (flag)
                    throw new RuntimeException("程序异常");
                producer.send(new ProducerRecord<String, String>("test", "3")).get();
                producer.send(new ProducerRecord<String, String>("test", "4")).get();
                //事务提交
                producer.commitTransaction();
            } catch (Exception e) {
                //中止事务
                producer.abortTransaction();
                e.printStackTrace();
            }
        }
    }
}