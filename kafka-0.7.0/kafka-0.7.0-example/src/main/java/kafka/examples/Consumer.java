/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kafka.examples;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaMessageStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.Message;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Consumer extends Thread {
    private final ConsumerConnector consumer;
    private final String topic;
    
    public Consumer(String topic) {
        consumer = kafka.consumer.Consumer.createJavaConsumerConnector(createConsumerConfig());
        this.topic = topic;
    }
    
    private static ConsumerConfig createConsumerConfig() {
        Properties props = new Properties();
        props.put("zk.connect", KafkaProperties.zkConnect);
        props.put("groupid", KafkaProperties.groupId);
        props.put("zk.sessiontimeout.ms", "40000");
        props.put("zk.synctime.ms", "20000");
        props.put("autocommit.interval.ms", "10000");
        props.put("autocommit.enable", "false"); // 关闭自动提交offset
        
        return new ConsumerConfig(props);
        
    }
    
    public void run() {
        Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
        topicCountMap.put(topic, new Integer(1));
        Map<String, List<KafkaMessageStream<Message>>> consumerMap = consumer.createMessageStreams(topicCountMap);
        KafkaMessageStream<Message> stream = consumerMap.get(topic).get(0);
        ConsumerIterator<Message> it = stream.iterator();
//        System.out.println("消费kafka的消息数据： data:  "+ ExampleUtils.getMessage(it.next()));
    
        while (it.hasNext()) {
            System.out.println("消费kafka的消息数据： data:  "+ ExampleUtils.getMessage(it.next()));
        }
        
        System.out.println("-------------------手动提交offset--------1--------------------");
        consumer.commitOffsets();
        System.out.println("-------------------手动提交offset--------2--------------------");
        // 测试1： 不用关闭
        // 测试2：进行关闭
       // consumer.shutdown();
        
    }
}
