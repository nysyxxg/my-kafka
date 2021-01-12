package com.lun.kafka.demo;


/**
 * 使用多线线程消费kafka数据
 * @author admin
 *
 */
public class ConsumerMainTest {
	
	 public static void main(String[] args) {  
//       String zooKeeper = args[0];  
//       String groupId = args[1];  
//       String topic = args[2];  
//       int threads = Integer.parseInt(args[3]);  
   	
	   String zooKeeper = KafkaProperties.ZK_CONNECT;  
       String groupId = KafkaProperties.GroupId;  
       String topic = KafkaProperties.topicTest;
       int threads = 3;
  
       ConsumerGroupExample example = new ConsumerGroupExample(zooKeeper, groupId, topic);  
       example.run(threads);  
  
       try {  
           Thread.sleep(610000);
       } catch (InterruptedException ie) {  
  
       }  
       example.shutdown();  
   }  

}
