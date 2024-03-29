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

public interface KafkaProperties {
    // 单机模式
	 final static String Broker_List = "localhost:9092";
	 final static String ZK_CONNECT = "localhost:2181";
    
//    final static String Broker_List = "xxg.kafka.cn:9095";
//    final static String ZK_CONNECT = "xxg.kafka.cn:2181";

//    final static String Broker_List = "xxg.kafka.cn:9091,xxg.kafka.cn:9092,xxg.kafka.cn:9093";
//    final static String ZK_CONNECT = "xxg.kafka.cn:2181";
    // 集群模式
//	public final static String Broker_List = "localhost1:9093,localhost2:9094,localhost3:9095";
//	public final static String ZK_CONNECT = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183";
    
    final static String GroupId = "group-0821-test";
    
    final static String kafkaServerURL = "xxg.kafka.cn";
    final static int kafkaServerPort = 9092;
    final static int kafkaProducerBufferSize = 64 * 1024;
    final static int connectionTimeOut = 100000;
    final static int reconnectInterval = 10000;
    
    final static String topic_Test = "topic_test";
    final static String topic = "topic1";
    final static String topic1 = "topic1";
    final static String topic2 = "topic2";
    final static String topic3 = "topic3";
    final static String clientId = "SimpleConsumerDemoClient";
}
