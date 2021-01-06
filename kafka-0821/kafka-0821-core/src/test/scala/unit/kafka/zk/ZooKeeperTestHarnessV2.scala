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

package kafka.zk

import org.scalatest.junit.JUnit3Suite
import org.I0Itec.zkclient.ZkClient
import kafka.utils.{ZKStringSerializer, TestZKUtils, Utils}

trait ZooKeeperTestHarnessV2 extends JUnit3Suite {
//  val zkConnect = "127.0.0.1:2181"
  val zkConnect = "127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183"
  var zkClient: ZkClient = null
  val zkConnectionTimeout = 6000
  val zkSessionTimeout = 6000

  override def setUp() {
    super.setUp
    zkClient = new ZkClient(zkConnect, zkSessionTimeout, zkConnectionTimeout, ZKStringSerializer)
  }

  override def tearDown() {
//    Utils.swallow(zkClient.close())
//    Utils.swallow(zookeeper.shutdown())
//    super.tearDown
  }

}
