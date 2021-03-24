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

package kafka.consumer

import org.apache.log4j.Logger
import scala.collection.JavaConversions._
import kafka.utils.{Utils, ZkUtils, ZKStringSerializer}
import org.I0Itec.zkclient.{IZkStateListener, IZkChildListener, ZkClient}
import org.apache.zookeeper.Watcher.Event.KeeperState

class ZookeeperTopicEventWatcher(val config:ConsumerConfig,
    val eventHandler: TopicEventHandler[String]) {

  private val logger = Logger.getLogger(getClass)

  val lock = new Object()

  private var zkClient: ZkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs,
      config.zkConnectionTimeoutMs, ZKStringSerializer)

  startWatchingTopicEvents()

  private def startWatchingTopicEvents() {
    val topicEventListener = new ZkTopicEventListener
    ZkUtils.makeSurePersistentPathExists(zkClient, ZkUtils.BrokerTopicsPath)

    zkClient.subscribeStateChanges(
      new ZkSessionExpireListener(topicEventListener))

    val topics = zkClient.subscribeChildChanges(
      ZkUtils.BrokerTopicsPath, topicEventListener).toList

    // call to bootstrap topic list
    topicEventListener.handleChildChange(ZkUtils.BrokerTopicsPath, topics)
  }

  private def stopWatchingTopicEvents() { zkClient.unsubscribeAll() }

  def shutdown() {
    lock.synchronized {
      try {
        if (zkClient != null) {
          stopWatchingTopicEvents()
          zkClient.close()
          zkClient = null
        }
        else
          logger.warn("Cannot shutdown already shutdown topic event watcher.")
      }
      catch {
        case e =>
          logger.fatal(e)
          logger.fatal(Utils.stackTrace(e))
      }
    }
  }

  class ZkTopicEventListener() extends IZkChildListener {

    @throws(classOf[Exception])
    def handleChildChange(parent: String, children: java.util.List[String]) {
      lock.synchronized {
        try {
          if (zkClient != null) {
            val latestTopics = zkClient.getChildren(ZkUtils.BrokerTopicsPath).toList
            logger.debug("all topics: %s".format(latestTopics))

            eventHandler.handleTopicEvent(latestTopics)
          }
        }
        catch {
          case e =>
            logger.fatal(e)
            logger.fatal(Utils.stackTrace(e))
        }
      }
    }

  }

  class ZkSessionExpireListener(val topicEventListener: ZkTopicEventListener)
    extends IZkStateListener {

    @throws(classOf[Exception])
    def handleStateChanged(state: KeeperState) { }

    @throws(classOf[Exception])
    def handleNewSession() {
      lock.synchronized {
        if (zkClient != null) {
          logger.info(
            "ZK expired: resubscribing topic event listener to topic registry")
          zkClient.subscribeChildChanges(
            ZkUtils.BrokerTopicsPath, topicEventListener)
        }
      }
    }
  }
}

