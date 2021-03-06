/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka

import consumer.ConsumerConfig
import org.apache.log4j.Logger
import producer.ProducerConfig
import server.{KafkaConfig, KafkaServerStartable, KafkaServer}
import utils.Utils
import org.apache.log4j.jmx.LoggerDynamicMBean

object Kafka {
  private val logger = Logger.getLogger(Kafka.getClass)

  def main(args: Array[String]): Unit = {
    var args = Array(
      "D:\\my-kafka\\kafka-0.7.0\\config\\server.properties")
//    "D:\\my-kafka\\kafka-0.7.0\\config\\consumer.properties",
//    "D:\\my-kafka\\kafka-0.7.0\\config\\producer.properties"
    val kafkaLog4jMBeanName = "kafka:type=kafka.KafkaLog4j"
    Utils.swallow(logger.warn, Utils.registerMBean(new LoggerDynamicMBean(Logger.getRootLogger()), kafkaLog4jMBeanName))

    if (!List(1, 3).contains(args.length)) {
      println("USAGE: java [options] %s server.properties [consumer.properties producer.properties]".format(classOf[KafkaServer].getSimpleName()))
      System.exit(1)
    }
  
    try {
      val props = Utils.loadProps(args(0))
      val serverConfig = new KafkaConfig(props)

      val kafkaServerStartble = args.length match {
        case 3 =>
          val consumerConfig = new ConsumerConfig(Utils.loadProps(args(1)))
          val producerConfig = new ProducerConfig(Utils.loadProps(args(2)))
          new KafkaServerStartable(serverConfig, consumerConfig, producerConfig)
        case 1 =>
          new KafkaServerStartable(serverConfig)
      }

      /**
        * 这个方法的意思就是在jvm中增加一个关闭的钩子，
        * 当jvm关闭的时候，会执行系统中已经设置的所有通过方法addShutdownHook添加的钩子，
        * 当系统执行完这些钩子后，jvm才会关闭。所以这些钩子可以在jvm关闭的时候进行内存清理、对象销毁等操作。
        */
      // attach shutdown handler to catch control-c
      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() = {
          kafkaServerStartble.shutdown
          kafkaServerStartble.awaitShutdown
        }
      });

      kafkaServerStartble.startup
      kafkaServerStartble.awaitShutdown
    }
    catch {
      case e => logger.fatal(e)
    }
    System.exit(0)
  }
}
