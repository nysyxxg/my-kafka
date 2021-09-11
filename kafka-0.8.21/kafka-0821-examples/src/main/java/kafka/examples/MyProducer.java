package kafka.examples;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;

public class MyProducer extends Thread {
    private final kafka.javaapi.producer.Producer<String, String> producer;
    private final String topic;
    private final Properties props = new Properties();
    
    public MyProducer(String topic) {
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        /**
         * 笔者也是经常很长时间看源码分析，才明白了为什么ProducerConfig配置信息里面并不要求使用者提供完整的kafka集群的broker信息，
         * 而是任选一个或几个即可。因为他会通过您选择的broker和topics信息而获取最新的所有的broker信息。
         * 通过设置的broker来获取topic的元数据信息
         */
//        props.put("zookeeper.connect", KafkaProperties.ZK_CONNECT);
        props.put("metadata.broker.list", KafkaProperties.Broker_List);
        props.put("request.required.acks", "-1");
        // Use random partitioner. Don't need the key type. Just set it to
        // Integer.
        // The message is of type String.
        producer = new kafka.javaapi.producer.Producer<String, String>(new ProducerConfig(props));
        this.topic = topic;
    }
    
    public void run() {
        int messageNo = 1;
        while (true) {
            String messageStr = new String("Message_" + messageNo);
            System.out.println(messageStr);
            KeyedMessage keyedMessage = new KeyedMessage<String, String>(topic, messageStr);
            // 第一种发送方式
            producer.send(keyedMessage);
            // 第二种
//            producer.send(messages: java.util.List[KeyedMessage[K,V]]);
            messageNo++;
            if (messageNo == 10) {
                break;
            }
    
            try {
                Thread.sleep(3 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // ok
//        List<KeyedMessage<String, String>> messageList = new ArrayList<KeyedMessage<String, String>>();
//        for (int i = 1; i <= 100; i++) { //往3个分区发数据
//            String data = "message[The " + i + " message]" + i;
//            messageList.add(new KeyedMessage<String, String>(topic, i + "", data));
//            //String topic, String key, String message
//        }
//        producer.send(messageList);
    }
}
