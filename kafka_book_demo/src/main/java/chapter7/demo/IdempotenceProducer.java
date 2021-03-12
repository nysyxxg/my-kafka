package chapter7.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
 
/**
 *
 * 我们知道Kafka的消息交付可靠性保障分为 最多一次（at most once），至少一次（at least once），精确一次（exactly once）
 *
 * 1: 至少一次（at least once）
 *      什么时候Producer数据会重复发送呢???
 *      比如当Producer发送一条数据，当数据发送过去了，由于某种原因Broker没有反馈给Producer已经提交成功，
 *      Producer此时设置了重试机制，retries (设置方法：props.put(ProducerConfig.RETRIES_CONFIG, 5); ),
 *      则会再次发送数据，此时会导致数据重复发送
 * 2: 最多一次（at most once）
 *     与at least once 相反，我们把retries 禁止，则就是最多一次，如果禁止重试，会导致数据丢失
 * 3: 精确一次（exactly once）
 *    如何实现精确一次呢???
 *    Producer 有两种方法 幂等性与事务型
 *
 * 幂等性
 * 幂等性作用范围
 * 只能保证单个Producer不会产生重复数据，如果Producer重启或者多个Producer无法保证数据不重复
 * 实现方法
 * 设置一下配置即可 : props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG， true)
 *
 * 幂等性生产者
 *      它只能保证单分区上的幂等性，即一个幂等性 Producer 能够保证某个主题的一个 分区上不出现重复消息，它无法实现多个分区的幂等性
 *      它只能实现单会话上的幂等性，不能实现跨会话的幂等性。这里的会话，你可以理 解为 Producer 进程的一次运行。
 *      当你重启了 Producer 进程之后，这种幂等性保 证就丧失了
 * @date 2020/4/19 22:38
 */
public class IdempotenceProducer {
 
    private static final String KAFKA_BROKERS = "localhost:9092";
    private  static Producer<String, String> producer ;
    public IdempotenceProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,  KAFKA_BROKERS);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024 * 1024 * 1024);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
 
        //设置Producer幂等性,其他不用变化
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true);
 
        producer = new KafkaProducer<String, String>(props);
 
    }
    public Producer<String,String> getProducer(){
        return producer;
    }
    public static void main(String[] args) throws ExecutionException, InterruptedException {
 
        IdempotenceProducer idempotenceProducer = new IdempotenceProducer();
        Producer<String, String> producer = idempotenceProducer.getProducer();
        producer.send(new ProducerRecord<String,String>("test","1234")).get();
 
    }
 
}