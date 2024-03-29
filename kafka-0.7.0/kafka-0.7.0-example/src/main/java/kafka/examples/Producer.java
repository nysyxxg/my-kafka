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

import kafka.javaapi.producer.ProducerData;
import kafka.producer.ProducerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Producer extends Thread {
    private final kafka.javaapi.producer.Producer<Integer, String> producer;
    private final String topic;
    private final Properties props = new Properties();
    
    public Producer(String topic) {
        props.put("serializer.class", "kafka.serializer.StringEncoder");
        props.put("zk.connect", "localhost:2181");
        props.put("zk.sessiontimeout.ms","600000");
        // Use random partitioner. Don't need the key type. Just set it to Integer.
        // The message is of type String.
        producer = new kafka.javaapi.producer.Producer<Integer, String>(new ProducerConfig(props));
        this.topic = topic;
    }
    
    public void run() {
        List<String> list = new ArrayList<String>();
        int messageNo =  21;
        while (true && messageNo <= 30) {
            String messageStr = new String("Message_" + messageNo);
//            producer.send(new ProducerData<Integer, String>(topic, messageStr));
           
            list.add(messageStr);
            messageNo++;
            System.out.println("Msg： " + messageStr);
        }
        producer.send(new ProducerData<Integer, String>(topic, null,list));
    
        // 测试1： 不用关闭
        // 测试2：进行关闭
        producer.close();
    }
    
}
