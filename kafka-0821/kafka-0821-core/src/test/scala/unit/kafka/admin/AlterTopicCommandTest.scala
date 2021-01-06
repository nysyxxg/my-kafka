/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kafka.admin

import junit.framework.Assert._
import org.junit.Test
import org.scalatest.junit.JUnit3Suite
import kafka.utils.Logging
import kafka.utils.TestUtils
import kafka.zk.ZooKeeperTestHarnessV2
import kafka.server.KafkaConfig
import kafka.admin.TopicCommand.TopicCommandOptions
import kafka.utils.ZkUtils

/**
 * 测试证明：topic的分区只能增加，不能减少的：
 * kafka.admin.AdminOperationException: 
 * The number of partitions for a topic can only be increased
 * 
 * 可以在线修改topic的分区，修改分区之后，消费端报错，不能负载均衡消费数据，
 *  错误：
 *  kafka.common.ConsumerRebalanceFailedException: 
 *  group1_DESKTOP-2HTL0OA-1512373308665-8ed0bf6f can't rebalance after 4 retries
 *  
 *  解决问题：
 *  需要重新启动[消费端]，但是不需要kafka服务器端
 */
class AlterTopicCommandTest extends JUnit3Suite with ZooKeeperTestHarnessV2 with Logging {

  @Test
  def testConfigPreservationAcrossPartitionAlteration() {
    val topic = "topic3"
    val cleanupKey = "cleanup.policy"
    val cleanupVal = "compact"
    // create brokers

    // modify the topic to add new partitions
    val numPartitionsModified = 3 // 修改为 3 个分区，需要重启消费端
    val alterOpts = new TopicCommandOptions(Array("--partitions", numPartitionsModified.toString,
      "--config", cleanupKey + "=" + cleanupVal,
      "--topic", topic))
    TopicCommand.alterTopic(zkClient, alterOpts)
    val newProps = AdminUtils.fetchTopicConfig(zkClient, topic)
    assertTrue("Updated properties do not contain " + cleanupKey, newProps.containsKey(cleanupKey))
    assertTrue("Updated properties have incorrect value", newProps.getProperty(cleanupKey).equals(cleanupVal))
  }
}