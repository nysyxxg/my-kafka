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


import metrics.KafkaMetricsReporter
import server.{KafkaConfig, KafkaServerStartable, KafkaServer}
import utils.{Utils, Logging}

/**
 * kafka           --kafka门面入口类，副本管理，topic配置管理，leader选举实现(由contoroller模块调用)。
 * 
 * kafka为kafka broker的main启动类，其主要作用为加载配置，启动report服务(内部状态的监控)，注册释放资源的钩子，以及门面入口类。
 * 
 * Kafka启动程序的主要入口
 */
object KafkaServer03 extends Logging {

  def main(args: Array[String]): Unit = {
    var args = new Array[String](1);
    args(0) = "../config/server-3.properties"
    print(args(0))
    if (args.length != 1) {
      println("USAGE: java [options] %s server.properties".format(classOf[KafkaServer].getSimpleName()))
      System.exit(1)
    }
  
    try {
      val props = Utils.loadProps(args(0)) //加载配置文件  
      val serverConfig = new KafkaConfig(props)
      KafkaMetricsReporter.startReporters(serverConfig.props)   //启动report服务(内部状态的监控)  
      val kafkaServerStartable = new KafkaServerStartable(serverConfig) //kafka server核心入口类  

      // attach shutdown handler to catch control-c
      // //钩子程序，当jvm退出前，销毁所有资源  
      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() = {
          kafkaServerStartable.shutdown
        }
      })

      kafkaServerStartable.startup
      kafkaServerStartable.awaitShutdown
    }
    catch {
      case e: Throwable => fatal(e)
    }
    System.exit(0)
  }
}
