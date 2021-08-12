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

package kafka.consumer

import java.util.concurrent._
import java.util.concurrent.atomic._

import scala.collection._
import org.apache.log4j.Logger
import kafka.cluster._
import kafka.utils._
import org.I0Itec.zkclient.exception.ZkNodeExistsException
import java.net.InetAddress
import java.util

import org.I0Itec.zkclient.{IZkChildListener, IZkStateListener, ZkClient}
import org.apache.zookeeper.Watcher.Event.KeeperState
import kafka.api.OffsetRequest
import java.util.UUID

import kafka.serializer.Decoder
import kafka.common.InvalidConfigException

/**
  * This class handles the consumers interaction with zookeeper
  *
  * Directories:
  * 1. Consumer id registry:
  * /consumers/[group_id]/ids[consumer_id] -> topic1,...topicN
  * A consumer has a unique consumer id within a consumer group. A consumer registers its id as an ephemeral znode
  * and puts all topics that it subscribes to as the value of the znode. The znode is deleted when the client is gone.
  * A consumer subscribes to event changes of the consumer id registry within its group.
  *
  * The consumer id is picked up from configuration, instead of the sequential id assigned by ZK. Generated sequential
  * ids are hard to recover during temporary connection loss to ZK, since it's difficult for the client to figure out
  * whether the creation of a sequential znode has succeeded or not. More details can be found at
  * (http://wiki.apache.org/hadoop/ZooKeeper/ErrorHandling)
  *
  * 2. Broker node registry:
  * /brokers/[0...N] --> { "host" : "host:port",
  * "topics" : {"topic1": ["partition1" ... "partitionN"], ...,
  * "topicN": ["partition1" ... "partitionN"] } }
  * This is a list of all present broker brokers. A unique logical node id is configured on each broker node. A broker
  * node registers itself on start-up and creates a znode with the logical node id under /brokers. The value of the znode
  * is a JSON String that contains (1) the host name and the port the broker is listening to, (2) a list of topics that
  * the broker serves, (3) a list of logical partitions assigned to each topic on the broker.
  * A consumer subscribes to event changes of the broker node registry.
  *
  * 3. Partition owner registry:
  * /consumers/[group_id]/owner/[topic]/[broker_id-partition_id] --> consumer_node_id
  * This stores the mapping before broker partitions and consumers. Each partition is owned by a unique consumer
  * within a consumer group. The mapping is reestablished after each rebalancing.
  *
  * 4. Consumer offset tracking:
  * /consumers/[group_id]/offsets/[topic]/[broker_id-partition_id] --> offset_counter_value
  * Each consumer tracks the offset of the latest message consumed for each partition.
  *
  */
private[kafka] object ZookeeperConsumerConnector {
  val MAX_N_RETRIES = 4
  val shutdownCommand: FetchedDataChunk = new FetchedDataChunk(null, null, -1L)
}

/**
  * JMX interface for monitoring consumer
  */
trait ZookeeperConsumerConnectorMBean {
  def getPartOwnerStats: String

  def getConsumerGroup: String

  def getOffsetLag(topic: String, brokerId: Int, partitionId: Int): Long

  def getConsumedOffset(topic: String, brokerId: Int, partitionId: Int): Long

  def getLatestOffset(topic: String, brokerId: Int, partitionId: Int): Long
}

private[kafka] class ZookeeperConsumerConnector(val config: ConsumerConfig,
                                                val enableFetcher: Boolean) // for testing only
  extends ConsumerConnector with ZookeeperConsumerConnectorMBean {
  println(getClass() + "------------------------ZookeeperConsumerConnector----init------")
  private val logger = Logger.getLogger(getClass())
  private val isShuttingDown = new AtomicBoolean(false)
  private val rebalanceLock = new Object //  重新平衡加一把锁
  private var fetcher: Option[Fetcher] = None
  private var zkClient: ZkClient = null
  private val topicRegistry = new Pool[String, Pool[Partition, PartitionTopicInfo]] // topic--> Partition, PartitionTopicInfo
  // queues : (topic,consumerThreadId) -> queue
  private val queues = new Pool[Tuple2[String, String], BlockingQueue[FetchedDataChunk]]
  private val scheduler = new KafkaScheduler(1, "Kafka-consumer-autocommit-", false)
  connectZk()
  createFetcher()
  if (config.autoCommit) { // 判断是否自动提交offset，如果是需要开启定时线程
    logger.info("starting auto committer every " + config.autoCommitIntervalMs + " ms")
    scheduler.scheduleWithRate(autoCommit, config.autoCommitIntervalMs, config.autoCommitIntervalMs) // 默认10秒提交一次
  }

  def this(config: ConsumerConfig) = this(config, true)

  def createMessageStreams[T](topicCountMap: Map[String, Int],
                              decoder: Decoder[T])
  : Map[String, List[KafkaMessageStream[T]]] = {
    println(getClass() + "------------------------createMessageStreams--------")
    consume(topicCountMap, decoder)
  }

  private def createFetcher() {
    println(getClass() + "------------------------createFetcher---------")
    if (enableFetcher)
      fetcher = Some(new Fetcher(config, zkClient))
  }

  private def connectZk() {
    println(getClass() + "------------------------connectZk---------")
    logger.info("Connecting to zookeeper instance at " + config.zkConnect)
    zkClient = new ZkClient(config.zkConnect, config.zkSessionTimeoutMs, config.zkConnectionTimeoutMs, ZKStringSerializer)
  }

  def shutdown() {
    val canShutdown = isShuttingDown.compareAndSet(false, true);
    if (canShutdown) {
      logger.info("ZKConsumerConnector shutting down")
      try {
        scheduler.shutdownNow()
        fetcher match {
          case Some(f) => f.shutdown()
          case None =>
        }
        sendShudownToAllQueues()
        if (config.autoCommit)
          commitOffsets()
        if (zkClient != null) {
          zkClient.close()
          zkClient = null
        }
      }
      catch {
        case e =>
          logger.fatal("error during consumer connector shutdown", e)
      }
      logger.info("ZKConsumerConnector shut down completed")
    }
  }

  def consume[T](topicCountMap: scala.collection.Map[String, Int],
                 decoder: Decoder[T])
  : Map[String, List[KafkaMessageStream[T]]] = {
    println(getClass() + "---------------------entering---consume--------")
    logger.debug("entering consume ")
    if (topicCountMap == null){
      throw new RuntimeException("topicCountMap is null")
    }
    val dirs = new ZKGroupDirs(config.groupId)
    println(getClass() + "------------------entering------consume--------dirs= "+ dirs)
    var ret = new mutable.HashMap[String, List[KafkaMessageStream[T]]]

    var consumerUuid: String = null  // 创建消费者uuid
    config.consumerId match {
      case Some(consumerId) // for testing only 
      => consumerUuid = consumerId
      case None // generate unique consumerId automatically
      => val uuid = UUID.randomUUID()
        consumerUuid = "%s-%d-%s".format(
          InetAddress.getLocalHost.getHostName, System.currentTimeMillis,
          uuid.getMostSignificantBits().toHexString.substring(0, 8))
    }
    val consumerIdString = config.groupId + "_" + consumerUuid  // 拼接消费者id
    val topicCount = new TopicCount(consumerIdString, topicCountMap)

    // listener to consumer and partition changes
    // 初始化监听器，主要监听 消费者 和 partition的改变
    val loadBalancerListener = new ZKRebalancerListener(config.groupId, consumerIdString)
    registerConsumerInZK(dirs, consumerIdString, topicCount) // 注册消费者到zk

    // register listener for session expired event  为会话过期事件注册侦听器
    zkClient.subscribeStateChanges( // 订阅状态改变
      new ZKSessionExpireListenner(dirs, consumerIdString, topicCount, loadBalancerListener))
    // 订阅 子节点改变
    zkClient.subscribeChildChanges(dirs.consumerRegistryDir, loadBalancerListener)

    // create a queue per topic per consumer thread  为每个使用者线程的每个主题创建一个队列
    val consumerThreadIdsPerTopic = topicCount.getConsumerThreadIdsPerTopic
    for ((topic, threadIdSet) <- consumerThreadIdsPerTopic) {
      var streamList: List[KafkaMessageStream[T]] = Nil
      for (threadId <- threadIdSet) {
        val stream = new LinkedBlockingQueue[FetchedDataChunk](config.maxQueuedChunks) // 定义一个阻塞队列，存储获取的数据块
        queues.put((topic, threadId), stream)
        streamList ::= new KafkaMessageStream[T](topic, stream, config.consumerTimeoutMs, decoder)
      }
      ret += (topic -> streamList)
      logger.debug("adding topic " + topic + " and stream to map.............")

      // register on broker partition path changes
      val partitionPath = ZkUtils.BrokerTopicsPath + "/" + topic  // /brokers/topics/topicname
      ZkUtils.makeSurePersistentPathExists(zkClient, partitionPath) // 确保持久化节点路径存在
      zkClient.subscribeChildChanges(partitionPath, loadBalancerListener) // 监听这个目录子节点改变
    }

    // explicitly trigger load balancing for this consumer
    loadBalancerListener.syncedRebalance()  // 同步平衡
    ret
  }

  private def registerConsumerInZK(dirs: ZKGroupDirs, consumerIdString: String, topicCount: TopicCount) = {
    logger.info("begin registering consumer " + consumerIdString + " in ZK")
    ZkUtils.createEphemeralPathExpectConflict(zkClient, dirs.consumerRegistryDir + "/" + consumerIdString, topicCount.toJsonString)
    logger.info("end registering consumer " + consumerIdString + " in ZK")
  }

  private def sendShudownToAllQueues() = {
    for (queue <- queues.values) {
      logger.debug("Clearing up queue")
      queue.clear()
      queue.put(ZookeeperConsumerConnector.shutdownCommand)
      logger.debug("Cleared queue and sent shutdown command")
    }
  }

  def autoCommit() {
    if (logger.isTraceEnabled)
      logger.trace("auto committing")
    try {
      println(getClass() + "------------------------autoCommit--------" + Thread.currentThread().getName)
      commitOffsets()
    }
    catch {
      case t: Throwable =>
        // log it and let it go
        logger.error("exception during autoCommit: ", t)
    }
  }

  // 提交offset
  def commitOffsets() {
    println(getClass() + "------------------------commitOffsets--------" + Thread.currentThread().getName)
    if (zkClient == null) {
      return
    }
    for ((topic, infos) <- topicRegistry) {
      val topicDirs = new ZKGroupTopicDirs(config.groupId, topic)
      for (info <- infos.values) {
        val newOffset = info.getConsumeOffset
        try {
          ZkUtils.updatePersistentPath(zkClient, topicDirs.consumerOffsetDir + "/" + info.partition.name, newOffset.toString)
        } catch {
          case t: Throwable =>
            // log it and let it go
            logger.warn("exception during commitOffsets", t)
        }
        if (logger.isDebugEnabled)
          logger.debug("Committed offset " + newOffset + " for topic " + info)
      }
    }
  }

  // for JMX
  def getPartOwnerStats(): String = {
    val builder = new StringBuilder
    for ((topic, infos) <- topicRegistry) {
      builder.append("\n" + topic + ": [")
      val topicDirs = new ZKGroupTopicDirs(config.groupId, topic)
      for (partition <- infos.values) {
        builder.append("\n    {")
        builder.append {
          partition.partition.name
        }
        builder.append(",fetch offset:" + partition.getFetchOffset)
        builder.append(",consumer offset:" + partition.getConsumeOffset)
        builder.append("}")
      }
      builder.append("\n        ]")
    }
    builder.toString
  }

  // for JMX
  def getConsumerGroup(): String = config.groupId

  def getOffsetLag(topic: String, brokerId: Int, partitionId: Int): Long =
    getLatestOffset(topic, brokerId, partitionId) - getConsumedOffset(topic, brokerId, partitionId)

  def getConsumedOffset(topic: String, brokerId: Int, partitionId: Int): Long = {
    val partition = new Partition(brokerId, partitionId)
    val partitionInfos = topicRegistry.get(topic)
    if (partitionInfos != null) {
      val partitionInfo = partitionInfos.get(partition)
      if (partitionInfo != null)
        return partitionInfo.getConsumeOffset
    }

    //otherwise, try to get it from zookeeper
    try {
      val topicDirs = new ZKGroupTopicDirs(config.groupId, topic)
      val znode = topicDirs.consumerOffsetDir + "/" + partition.name
      val offsetString = ZkUtils.readDataMaybeNull(zkClient, znode)
      if (offsetString != null)
        return offsetString.toLong
      else
        return -1
    }
    catch {
      case e =>
        logger.error("error in getConsumedOffset JMX ", e)
    }
    return -2
  }

  def getLatestOffset(topic: String, brokerId: Int, partitionId: Int): Long =
    earliestOrLatestOffset(topic, brokerId, partitionId, OffsetRequest.LatestTime)

  private def earliestOrLatestOffset(topic: String, brokerId: Int, partitionId: Int, earliestOrLatest: Long): Long = {
    var simpleConsumer: SimpleConsumer = null
    var producedOffset: Long = -1L
    try {
      val cluster = ZkUtils.getCluster(zkClient)
      val broker = cluster.getBroker(brokerId)
      simpleConsumer = new SimpleConsumer(broker.host, broker.port, ConsumerConfig.SocketTimeout,
        ConsumerConfig.SocketBufferSize)
      val offsets = simpleConsumer.getOffsetsBefore(topic, partitionId, earliestOrLatest, 1)
      producedOffset = offsets(0)
    }
    catch {
      case e =>
        logger.error("error in earliestOrLatestOffset() ", e)
    }
    finally {
      if (simpleConsumer != null)
        simpleConsumer.close
    }
    producedOffset
  }

  class ZKSessionExpireListenner(val dirs: ZKGroupDirs,
                                 val consumerIdString: String,
                                 val topicCount: TopicCount,
                                 val loadBalancerListener: ZKRebalancerListener)
    extends IZkStateListener {

    //    @throws(classOf[Exception])
    //    def handleStateChanged(state: KeeperState) {
    // do nothing, since zkclient will do reconnect for us.
    //    }

    /**
      * Called after the zookeeper session has expired and a new session has been created. You would have to re-create
      * any ephemeral nodes here.
      *
      * @throws Exception
      * On any error.
      */
    @throws(classOf[Exception])
    def handleNewSession() {
      /**
        * When we get a SessionExpired event, we lost all ephemeral nodes and zkclient has reestablished a
        * connection for us. We need to release the ownership of the current consumer and re-register this
        * consumer in the consumer registry and trigger a rebalance.
        */
      logger.info("ZK expired; release old broker parition ownership; re-register consumer " + consumerIdString)
      loadBalancerListener.resetState
      registerConsumerInZK(dirs, consumerIdString, topicCount)
      // explicitly trigger load balancing for this consumer
      loadBalancerListener.syncedRebalance

      // There is no need to resubscribe to child and state changes.
      // The child change watchers will be set inside rebalance when we read the children list. 
    }

    @throws(classOf[Exception])
    def handleStateChanged(keeperState: KeeperState) = {

    }
  }


  class ZKRebalancerListener(val group: String, val consumerIdString: String) extends IZkChildListener {
    private val dirs = new ZKGroupDirs(group)
    private var oldPartitionsPerTopicMap: mutable.Map[String, List[String]] = new mutable.HashMap[String, List[String]]()
    private var oldConsumersPerTopicMap: mutable.Map[String, List[String]] = new mutable.HashMap[String, List[String]]()

    //    @throws(classOf[Exception])
    //    def handleChildChange(parentPath : String, curChilds : java.util.List[String]) {
    //      syncedRebalance
    //    }

    private def releasePartitionOwnership() {
      for ((topic, infos) <- topicRegistry) {
        val topicDirs = new ZKGroupTopicDirs(group, topic)
        for (partition <- infos.keys) {
          val znode = topicDirs.consumerOwnerDir + "/" + partition
          ZkUtils.deletePath(zkClient, znode)
          if (logger.isDebugEnabled)
            logger.debug("Consumer " + consumerIdString + " releasing " + znode)
        }
      }
    }

    private def getConsumersPerTopic(group: String): mutable.Map[String, List[String]] = {
      val consumers = ZkUtils.getChildrenParentMayNotExist(zkClient, dirs.consumerRegistryDir)
      val consumersPerTopicMap = new mutable.HashMap[String, List[String]]
      for (consumer <- consumers) {
        val topicCount = getTopicCount(consumer)
        for ((topic, consumerThreadIdSet) <- topicCount.getConsumerThreadIdsPerTopic()) {
          for (consumerThreadId <- consumerThreadIdSet)
            consumersPerTopicMap.get(topic) match {
              case Some(curConsumers) => consumersPerTopicMap.put(topic, consumerThreadId :: curConsumers)
              case _ => consumersPerTopicMap.put(topic, List(consumerThreadId))
            }
        }
      }
      for ((topic, consumerList) <- consumersPerTopicMap)
        consumersPerTopicMap.put(topic, consumerList.sortWith((s, t) => s < t))
      consumersPerTopicMap
    }

    private def getRelevantTopicMap(myTopicThreadIdsMap: Map[String, Set[String]],
                                    newPartMap: Map[String, List[String]],
                                    oldPartMap: Map[String, List[String]],
                                    newConsumerMap: Map[String, List[String]],
                                    oldConsumerMap: Map[String, List[String]]): Map[String, Set[String]] = {
      var relevantTopicThreadIdsMap = new mutable.HashMap[String, Set[String]]()
      for ((topic, consumerThreadIdSet) <- myTopicThreadIdsMap)
        if (oldPartMap.get(topic) != newPartMap.get(topic) || oldConsumerMap.get(topic) != newConsumerMap.get(topic))
          relevantTopicThreadIdsMap += (topic -> consumerThreadIdSet)
      relevantTopicThreadIdsMap
    }

    private def getTopicCount(consumerId: String): TopicCount = {
      val path = dirs.consumerRegistryDir + "/" + consumerId
      val topicCountJson = ZkUtils.readData(zkClient, path)
      TopicCount.constructTopicCount(consumerId, topicCountJson)
    }

    def resetState() {
      topicRegistry.clear
      oldConsumersPerTopicMap.clear
      oldPartitionsPerTopicMap.clear
    }

    def syncedRebalance() { // 同步平衡
      rebalanceLock synchronized {
        for (i <- 0 until ZookeeperConsumerConnector.MAX_N_RETRIES) {
          logger.info("begin rebalancing consumer " + consumerIdString + " try #" + i)
          var done = false
          try {
            done = rebalance()
          } catch {
            case e =>
              // occasionally, we may hit a ZK exception because the ZK state is changing while we are iterating.
              // For example, a ZK node can disappear between the time we get all children and the time we try to get
              // the value of a child. Just let this go since another rebalance will be triggered.
              logger.info("exception during rebalance ", e)
          }
          logger.info("end rebalancing consumer " + consumerIdString + " try #" + i)
          if (done)
            return
          // release all partitions, reset state and retry
          releasePartitionOwnership()
          resetState()
          Thread.sleep(config.zkSyncTimeMs)
        }
      }
      throw new RuntimeException(consumerIdString + " can't rebalance after " + ZookeeperConsumerConnector.MAX_N_RETRIES + " retires")
    }

    private def rebalance(): Boolean = {
      val myTopicThreadIdsMap = getTopicCount(consumerIdString).getConsumerThreadIdsPerTopic
      val cluster = ZkUtils.getCluster(zkClient)
      val consumersPerTopicMap = getConsumersPerTopic(group)
      val partitionsPerTopicMap = ZkUtils.getPartitionsForTopics(zkClient, myTopicThreadIdsMap.keys.iterator)
      val relevantTopicThreadIdsMap = getRelevantTopicMap(myTopicThreadIdsMap, partitionsPerTopicMap, oldPartitionsPerTopicMap, consumersPerTopicMap, oldConsumersPerTopicMap)
      if (relevantTopicThreadIdsMap.size <= 0) {
        logger.info("Consumer " + consumerIdString + " with " + consumersPerTopicMap + " doesn't need to rebalance.")
        return true
      }

      logger.info("Committing all offsets")
      commitOffsets

      logger.info("Releasing partition ownership")
      releasePartitionOwnership()

      val queuesToBeCleared = new mutable.HashSet[BlockingQueue[FetchedDataChunk]]
      for ((topic, consumerThreadIdSet) <- relevantTopicThreadIdsMap) {
        topicRegistry.remove(topic)
        topicRegistry.put(topic, new Pool[Partition, PartitionTopicInfo])

        val topicDirs = new ZKGroupTopicDirs(group, topic)
        val curConsumers = consumersPerTopicMap.get(topic).get
        var curPartitions: List[String] = partitionsPerTopicMap.get(topic).get

        val nPartsPerConsumer = curPartitions.size / curConsumers.size
        val nConsumersWithExtraPart = curPartitions.size % curConsumers.size

        logger.info("Consumer " + consumerIdString + " rebalancing the following partitions: " + curPartitions +
          " for topic " + topic + " with consumers: " + curConsumers)

        for (consumerThreadId <- consumerThreadIdSet) {

          //          val myConsumerPosition = curConsumers.findIndexOf(_ == consumerThreadId)
          //          assert(myConsumerPosition >= 0)
          // xxg
          val myConsumerPosition = curConsumers.indexOf(consumerThreadId)
          assert(myConsumerPosition >= 0)

          val startPart = nPartsPerConsumer * myConsumerPosition + myConsumerPosition.min(nConsumersWithExtraPart)
          val nParts = nPartsPerConsumer + (if (myConsumerPosition + 1 > nConsumersWithExtraPart) 0 else 1)

          /**
            * Range-partition the sorted partitions to consumers for better locality.
            * The first few consumers pick up an extra partition, if any.
            */
          if (nParts <= 0)
            logger.warn("No broker partitions consumed by consumer thread " + consumerThreadId + " for topic " + topic)
          else {
            for (i <- startPart until startPart + nParts) {
              val partition = curPartitions(i)
              logger.info(consumerThreadId + " attempting to claim partition " + partition)
              if (!processPartition(topicDirs, partition, topic, consumerThreadId))
                return false
            }
            queuesToBeCleared += queues.get((topic, consumerThreadId))
          }
        }
      }
      updateFetcher(cluster, queuesToBeCleared)
      oldPartitionsPerTopicMap = partitionsPerTopicMap
      oldConsumersPerTopicMap = consumersPerTopicMap
      true
    }

    private def updateFetcher(cluster: Cluster, queuesTobeCleared: Iterable[BlockingQueue[FetchedDataChunk]]) {
      // update partitions for fetcher
      var allPartitionInfos: List[PartitionTopicInfo] = Nil
      for (partitionInfos <- topicRegistry.values)
        for (partition <- partitionInfos.values)
          allPartitionInfos ::= partition
      logger.info("Consumer " + consumerIdString + " selected partitions : " +
        allPartitionInfos.sortWith((s, t) => s.partition < t.partition).map(_.toString).mkString(","))

      fetcher match {
        case Some(f) => f.initConnections(allPartitionInfos, cluster, queuesTobeCleared)
        case None =>
      }
    }

    private def processPartition(topicDirs: ZKGroupTopicDirs, partition: String,
                                 topic: String, consumerThreadId: String): Boolean = {
      val partitionOwnerPath = topicDirs.consumerOwnerDir + "/" + partition // 创建消费者消费这个分区的临时目录
      try {
        ZkUtils.createEphemeralPathExpectConflict(zkClient, partitionOwnerPath, consumerThreadId)
      }
      catch {
        case e: ZkNodeExistsException =>
          // The node hasn't been deleted by the original owner. So wait a bit and retry.
          logger.info("waiting for the partition ownership to be deleted: " + partition)
          return false
        case e2 => throw e2
      }
      addPartitionTopicInfo(topicDirs, partition, topic, consumerThreadId)
      true
    }

    private def addPartitionTopicInfo(topicDirs: ZKGroupTopicDirs, partitionString: String,
                                      topic: String, consumerThreadId: String) {
      val partition = Partition.parse(partitionString)
      val partTopicInfoMap = topicRegistry.get(topic)

      val znode = topicDirs.consumerOffsetDir + "/" + partition.name
      val offsetString = ZkUtils.readDataMaybeNull(zkClient, znode)
      // If first time starting a consumer, set the initial offset based on the config
      var offset: Long = 0L
      if (offsetString == null){
        offset = config.autoOffsetReset match { // 根据设置autoOffsetReset，来获取offset的消费位置
          case OffsetRequest.SmallestTimeString => // 从 smallest开始
            earliestOrLatestOffset(topic, partition.brokerId, partition.partId, OffsetRequest.EarliestTime)
          case OffsetRequest.LargestTimeString => // 从 largest 开始
            earliestOrLatestOffset(topic, partition.brokerId, partition.partId, OffsetRequest.LatestTime)
          case _ =>
            throw new InvalidConfigException("Wrong value in autoOffsetReset in ConsumerConfig")
        }
      }else{
        offset = offsetString.toLong
      }
      val queue = queues.get((topic, consumerThreadId))
      val consumedOffset = new AtomicLong(offset)
      val fetchedOffset = new AtomicLong(offset)
      val partTopicInfo = new PartitionTopicInfo(topic,
        partition.brokerId,
        partition,
        queue,
        consumedOffset,
        fetchedOffset,
        new AtomicInteger(config.fetchSize))
      partTopicInfoMap.put(partition, partTopicInfo)
      if (logger.isDebugEnabled){
        logger.debug(partTopicInfo + " selected new offset " + offset)
      }
    }

    override def handleChildChange(s: String, list: util.List[String]): Unit = {

    }
  }

}

