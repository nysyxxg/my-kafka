package chapter2;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

/**
 * 代码清单2-4
 * Created by 朱小厮 on 2018/7/26.
 */
public class ProducerSelfSerializer {
    public static final String brokerList = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
    public static final String topic = "topic-demo";
    
    public static void main(String[] args)
            throws ExecutionException, InterruptedException {
        Properties properties = new Properties();
        // 发送数据要经过下面过程
        // 1: 首先经过 拦截器
        properties.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, ProducerInterceptorPrefix.class.getName());
        
        // 2： 经过 序列化器
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // 使用自定义的序列化
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CompanySerializer.class.getName());
//        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,ProtostuffSerializer.class.getName());
        properties.put("bootstrap.servers", brokerList);
        // 3: 经过 自定义分区
        properties.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, DemoPartitioner.class.getName());
        
        KafkaProducer<String, Company> producer = new KafkaProducer<>(properties);
        // 构建发送对象
        Company company = Company.builder().name("hiddenkafka").address("China").build();
//        Company company = Company.builder().name("hiddenkafka").address("China").telphone("13000000000").build();
        ProducerRecord<String, Company> record = new ProducerRecord<>(topic, company);
        producer.send(record).get();
    }
}
