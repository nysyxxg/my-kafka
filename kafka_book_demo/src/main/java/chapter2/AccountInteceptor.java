package chapter2;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;

/**
 * 生产者拦截器
 */
public class AccountInteceptor implements ProducerInterceptor {
    //account for success/fail with event sended
    int success = 0;
    int fail = 0;
    
    @Override
    public ProducerRecord onSend(ProducerRecord record) {
        return new ProducerRecord(record.topic(), record.partition(), record.timestamp(), record.key(),
                System.currentTimeMillis() + "：" + record.value().toString());
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        if (metadata != null) {
            success++;
        } else {
            fail++;
        }
    }
    
    @Override
    public void close() {
        System.out.println("success:" + success + "  fail:" + fail);
    }
    
    @Override
    public void configure(Map<String, ?> configs) {
    }
}