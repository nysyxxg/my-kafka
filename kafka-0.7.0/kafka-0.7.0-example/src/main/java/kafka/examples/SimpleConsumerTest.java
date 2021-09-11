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

import kafka.api.FetchRequest;
import kafka.javaapi.MultiFetchResponse;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.message.MessageAndOffset;

import java.util.ArrayList;
import java.util.List;


public class SimpleConsumerTest {
    private static void printMessages(ByteBufferMessageSet messageSet) {
        // 开始遍历读取到的消息的迭代器
        for (MessageAndOffset messageAndOffset : messageSet) {
            System.out.println("******************消费的消息:  " + ExampleUtils.getMessage(messageAndOffset.message()));
        }
    }
    
    
    public static void main(String[] args) {
        
        SimpleConsumer simpleConsumer = new SimpleConsumer(KafkaProperties.kafkaServerURL,
                KafkaProperties.kafkaServerPort,
                KafkaProperties.connectionTimeOut,
                KafkaProperties.kafkaProducerBufferSize);
        
        System.out.println("Testing single fetch");
        // 指定topic，指定partition，执定offset消费
        FetchRequest req = new FetchRequest(KafkaProperties.topic, 0, 1575L, 2000);
        ByteBufferMessageSet messageSet = simpleConsumer.fetch(req);
        printMessages(messageSet);
        
//        System.out.println("Testing single multi-fetch");
//        FetchRequest req2 = new FetchRequest(KafkaProperties.topic2, 0, 0L, 100);
//        List<FetchRequest> list = new ArrayList<FetchRequest>();
//        list.add(req2);
//
//        FetchRequest req3 = new FetchRequest(KafkaProperties.topic3, 0, 0L, 100);
//        list.add(req3);
//
//        MultiFetchResponse response = simpleConsumer.multifetch(list);
//        int fetchReq = 0;
//        for (ByteBufferMessageSet resMessageSet : response) {
//            System.out.println("Response from fetch request no: fetchReq = " + (++fetchReq));
//            printMessages(resMessageSet);
//        }
    }
    
}
