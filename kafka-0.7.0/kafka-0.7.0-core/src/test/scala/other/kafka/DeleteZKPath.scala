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
import utils.{ZKStringSerializer, ZkUtils, Utils}
import org.I0Itec.zkclient.ZkClient

/**
  * 删除ZK的目录
  */
object DeleteZKPath {

  def main(args: Array[String]) {
    val args = Array("D:\\my-kafka\\kafka-0.7.0\\config\\consumer.properties","/consumers/test-consumer-group1")
    if(args.length < 2) {
      println("USAGE: " + DeleteZKPath.getClass.getName + " consumer.properties zk_path")
      System.exit(1)
    }

    val config = new ConsumerConfig(Utils.loadProps(args(0)))
    val zkPath = args(1)

    val zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs, config.zkConnectionTimeoutMs,
      ZKStringSerializer)

    try {
      ZkUtils.deletePathRecursive(zkClient, zkPath); // 递归删zk节点目录
      System.out.println(zkPath + " is deleted")
    } catch {
      case e: Exception => System.err.println("Path not deleted " + e.printStackTrace())
    }
  }
}
