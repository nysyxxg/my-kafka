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

import java.util.ArrayList;
import java.util.List;

import kafka.examples.util.DateUtil;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.message.MessageAndOffset;
import kafka.utils.SystemTime;
import scala.collection.Iterator;

import kafka.api.FetchRequest;
import kafka.message.Message;


public class SimpleConsumerDemo {
    private static void printMessages( kafka.javaapi.message.ByteBufferMessageSet messageSet) {
        for (MessageAndOffset messageAndOffset : messageSet) {
            System.out.println("******************消费的消息:  " + ExampleUtils.getMessage(messageAndOffset.message()));
        }
    }
    
//    private static void printMessagesV2(kafka.message.ByteBufferMessageSet messageSet) {
//        for (MessageAndOffset messageAndOffset : messageSet) {
//            System.out.println(ExampleUtils.getMessage(messageAndOffset.message()));
//        }
//    }
    
    
    private static void generateData() {
        Producer producer2 = new Producer(KafkaProperties.topic2);
        producer2.start();
        Producer producer3 = new Producer(KafkaProperties.topic3);
        producer3.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        
       // generateData();
        SimpleConsumer simpleConsumer = new SimpleConsumer(KafkaProperties.kafkaServerURL,
                KafkaProperties.kafkaServerPort,
                KafkaProperties.connectionTimeOut,
                KafkaProperties.kafkaProducerBufferSize);
        
        System.out.println("Testing single fetch");
        System.out.println(DateUtil.getDateFormat()+"----------0--------fetch------------------------");
        FetchRequest req = new FetchRequest(KafkaProperties.topic, 0, 1575L, 2010);
        
        System.out.println(DateUtil.getDateFormat()+"----------1--------fetch------------------------");
        kafka.javaapi.message.ByteBufferMessageSet messageSet = simpleConsumer.fetch(req);
        
        System.out.println(DateUtil.getDateFormat()+"----------2--------fetch------------------------");
        printMessages(messageSet);
        System.out.println(DateUtil.getDateFormat()+"----------3--------fetch------------------------");

//        System.out.println("Testing single multi-fetch");
//        req = new FetchRequest(KafkaProperties.topic2, 0, 0L, 100);
//        List<FetchRequest> list = new ArrayList<FetchRequest>();
//        list.add(req);
//        req = new FetchRequest(KafkaProperties.topic3, 0, 0L, 100);
//        list.add(req);
//        kafka.javaapi.MultiFetchResponse response = simpleConsumer.multifetch(list);
//        int fetchReq = 0;
        
//        for (kafka.javaapi.message.ByteBufferMessageSet resMessageSet : response) {
//            System.out.println("Response from fetch request no: " + ++fetchReq);
//            printMessages(resMessageSet);
//        }
        
//        for (kafka.message.ByteBufferMessageSet resMessageSet : response) {
//            System.out.println("Response from fetch request no: " + ++fetchReq);
//            printMessagesV2(resMessageSet);
//        }
    
    
    }
    
}
