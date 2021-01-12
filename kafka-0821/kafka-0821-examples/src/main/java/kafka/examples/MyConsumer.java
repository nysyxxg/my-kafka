package kafka.examples;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;


public class MyConsumer extends Thread {
    private final ConsumerConnector consumer;
    private final String topic;
    
    public MyConsumer(String topic) {
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(createConsumerConfig());
        this.topic = topic;
    }
    
    private static ConsumerConfig createConsumerConfig() {
        Properties props = new Properties();
        props.put("zookeeper.connect", KafkaProperties.ZK_CONNECT);
        props.put("group.id", KafkaProperties.GroupId);
        props.put("zookeeper.session.timeout.ms", "5000");
        props.put("zookeeper.connection.timeout.ms", "10000");
        props.put("zookeeper.sync.time.ms", "2000");
        props.put("auto.commit.interval.ms", "1000");
        props.put("auto.offset.reset", "smallest");

//          zookeeper.session.timeout.ms=5000
//    		zookeeper.connection.timeout.ms=10000
//    		zookeeper.sync.time.ms=2000
        
        return new ConsumerConfig(props);
        
    }
    
    public void run() {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(1));
        Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        KafkaStream<byte[], byte[]> stream = consumerMap.get(topic).get(0);
        ConsumerIterator<byte[], byte[]> it = stream.iterator();
        while (it.hasNext()) {
            //System.out.println(new String(it.next().message()));
            
            MessageAndMetadata<byte[], byte[]> message = it.next();
            String topic = message.topic();
            int partition = message.partition();
            long offset = message.offset();
            byte[] keyByte = message.key();
            String key = null;
            if (keyByte != null) {
                key = new String(keyByte);
            }
            String msg = new String(message.message());
            // 在这里处理消息,这里仅简单的输出
            // 如果消息消费失败，可以将已上信息打印到日志中，活着发送到报警短信和邮件中，以便后续处理
            System.out.println("consumerid:" + KafkaProperties.clientId + ", thread : " + Thread.currentThread().getName()
                    + ", topic : " + topic + ", partition : " + partition
                    + ", offset : " + offset + " , key : " + key + " , mess : " + msg);
        }
        
    }
}
