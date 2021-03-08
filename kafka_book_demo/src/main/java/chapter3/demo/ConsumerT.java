package chapter3.demo;

import chapter3.ConsumerInterceptorTTL;
import org.apache.kafka.clients.consumer.*;
import java.util.Arrays;
import java.util.Properties;

public class ConsumerT {
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,  "chdp11:9092，chdp12:9092，chdp12:9092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tg");
        //turn off autocommit
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        //props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        
        // 设置消费者拦截器
        props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG , ConsumerInterceptorTTL.class.getName());
        
        //2
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        //3
        consumer.subscribe(Arrays.asList("mytp"));
        System.out.println("waiting for msg:");
        //4
        while(true){
            ConsumerRecords<String, String> recodes = consumer.poll(100);
            for (ConsumerRecord<String,String> recode:recodes){
                System.out.println("offset:"+recode.offset() +" key:"+recode.key()+ " value:"+recode.value());
            }
            /**
             * This is a synchronous commits and will block until either the commit succeeds or an unrecoverable error is
             *   encountered (in which case it is thrown to the caller).
             */
            //consumer.commitSync();
            /**
             *  * This is an asynchronous call and will not block. Any errors encountered are either passed to the callback
             *   (if provided) or discarded.
             */
            consumer.commitAsync((offsets,exception) ->{
               if(exception!=null){
                   //just a simple test
                    exception.printStackTrace();
               }
               else{
                   //System.out.println("commit success for offset end:"+offsets.values());
               }
            });
        }
    }
}
