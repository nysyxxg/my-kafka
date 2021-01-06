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

import java.util.Properties
import kafka.api.OffsetRequest
import kafka.utils._
import kafka.common.{InvalidConfigException, Config}

/**
 * 
 */
object ConsumerConfig extends Config {
  val RefreshMetadataBackoffMs = 200
  val SocketTimeout = 30 * 1000
  val SocketBufferSize = 64*1024
  val FetchSize = 1024 * 1024
  val MaxFetchSize = 10*FetchSize
  val NumConsumerFetchers = 1
  val DefaultFetcherBackoffMs = 1000
  val AutoCommit = true
  val AutoCommitInterval = 60 * 1000
  val MaxQueuedChunks = 2
  val MaxRebalanceRetries = 4
  val AutoOffsetReset = OffsetRequest.LargestTimeString
  val ConsumerTimeoutMs = -1
  val MinFetchBytes = 1
  val MaxFetchWaitMs = 100
  val MirrorTopicsWhitelist = ""
  val MirrorTopicsBlacklist = ""
  val MirrorConsumerNumThreads = 1
  val OffsetsChannelBackoffMs = 1000
  val OffsetsChannelSocketTimeoutMs = 10000
  val OffsetsCommitMaxRetries = 5
  val OffsetsStorage = "zookeeper"

  val MirrorTopicsWhitelistProp = "mirror.topics.whitelist"
  val MirrorTopicsBlacklistProp = "mirror.topics.blacklist"
  val ExcludeInternalTopics = true
  val DefaultPartitionAssignmentStrategy = "range" /* select between "range", and "roundrobin" */
  val MirrorConsumerNumThreadsProp = "mirror.consumer.numthreads"
  val DefaultClientId = ""

  def validate(config: ConsumerConfig) {
    validateClientId(config.clientId)
    validateGroupId(config.groupId)
    validateAutoOffsetReset(config.autoOffsetReset)
    validateOffsetsStorage(config.offsetsStorage)
  }

  def validateClientId(clientId: String) {
    validateChars("client.id", clientId)
  }

  def validateGroupId(groupId: String) {
    validateChars("group.id", groupId)
  }

  def validateAutoOffsetReset(autoOffsetReset: String) {
    autoOffsetReset match {
      case OffsetRequest.SmallestTimeString =>
      case OffsetRequest.LargestTimeString =>
      case _ => throw new InvalidConfigException("Wrong value " + autoOffsetReset + " of auto.offset.reset in ConsumerConfig; " +
                                                 "Valid values are " + OffsetRequest.SmallestTimeString + " and " + OffsetRequest.LargestTimeString)
    }
  }

  def validateOffsetsStorage(storage: String) {
    storage match {
      case "zookeeper" =>
      case "kafka" =>
      case _ => throw new InvalidConfigException("Wrong value " + storage + " of offsets.storage in consumer config; " +
                                                 "Valid values are 'zookeeper' and 'kafka'")
    }
  }
}

class ConsumerConfig private (val props: VerifiableProperties) extends ZKConfig(props) {
  import ConsumerConfig._

  def this(originalProps: Properties) {
    this(new VerifiableProperties(originalProps))
    props.verify()
  }

  /** a string that uniquely identifies a set of consumers within the same consumer group */
  //一个字符串用来指示一组consumer所在的组
  val groupId = props.getString("group.id")

  /** consumer id: generated automatically if not set.
   *  Set this explicitly for only testing purpose. */
  // consumer.id : 默认值： null，   不需要设置，一般自动产生
  val consumerId: Option[String] = Option(props.getString("consumer.id", null))

  /** the socket timeout for network requests. Its value should be at least fetch.wait.max.ms. */
  // socket.timeout.ms :默认值 30000  socket超时时间  
  val socketTimeoutMs = props.getInt("socket.timeout.ms", SocketTimeout)
  
  require(fetchWaitMaxMs <= socketTimeoutMs, "socket.timeout.ms should always be at least fetch.wait.max.ms" +
    " to prevent unnecessary socket timeouts")
  
  /** the socket receive buffer for network requests */
   //  socket.buffersize :默认值 64*1024 socket 接收数据缓存大小    socket receive buffer 
   //  socket用于接收网络请求的缓存大小
  val socketReceiveBufferBytes = props.getInt("socket.receive.buffer.bytes", SocketBufferSize)
  
  /** the number of byes of messages to attempt to fetch */
  // fetch.size 默认值：300 * 1024  控制在一个请求中获取的消息的字节数。 
  // 这个参数在0.8.x中由 fetch.message.max.bytes,fetch.min.bytes取代
  val fetchMessageMaxBytes = props.getInt("fetch.message.max.bytes", FetchSize)

  /** the number threads used to fetch data */
  // num.consumer.fetchers 默认值：  1,  用于fetch数据的fetcher线程数
  val numConsumerFetchers = props.getInt("num.consumer.fetchers", NumConsumerFetchers)
  
  /** if true, periodically commit to zookeeper the offset of messages already fetched by the consumer */
    //auto.commit.enable：默认值： true 如果true,consumer定期地往zookeeper写入每个分区的offset
    // 建议关闭自动提交，设置为false，手动更新zookeeper写入每个分区的offset
  val autoCommitEnable = props.getBoolean("auto.commit.enable", AutoCommit)
  
  /** the frequency in ms that the consumer offsets are committed to zookeeper */
  // auto.commit.interval.ms 默认值：  10000 往zookeeper上写offset的频率
  val autoCommitIntervalMs = props.getInt("auto.commit.interval.ms", AutoCommitInterval)

  /** max number of message chunks buffered for consumption, each chunk can be up to fetch.message.max.bytes*/
  // queued.max.message.chunks 默认值：  2 
  // high level consumer内部缓存拉回来的消息到一个队列中。 这个值控制这个队列的大小
  val queuedMaxMessages = props.getInt("queued.max.message.chunks", MaxQueuedChunks)

  /** max number of retries during rebalance */
  /**
   * rebalance.max.retries : 默认值： 4 
   * 当新的consumer加入到consumer group时，consumers集合试图重新平衡分配到每个consumer的partitions数目。
   * 如果consumers集合改变了，当分配正在执行时，这个重新平衡会失败并重入
   */
  val rebalanceMaxRetries = props.getInt("rebalance.max.retries", MaxRebalanceRetries)
  
  /** the minimum amount of data the server should return for a fetch request. 
   *  If insufficient data is available the request will block */
  /**
   * fetch.min.bytes :默认值： 1
   * 每次fetch请求时，server应该返回的最小字节数。如果没有足够的数据返回，请求会等待，直到足够的数据才会返回。
   */
  val fetchMinBytes = props.getInt("fetch.min.bytes", MinFetchBytes)
  
  /** the maximum amount of time the server will block before answering the fetch request if there isn't sufficient data to immediately satisfy fetch.min.bytes */
  // fetch.wait.max.ms : 拉取消息等待最大时间
  // 如果没有足够的数据能够满足fetch.min.bytes，则此项配置是指在应答fetch请求之前，server会阻塞的最大时间。
  val fetchWaitMaxMs = props.getInt("fetch.wait.max.ms", MaxFetchWaitMs)
  
  // backoff.increment.ms 1000  这个参数避免在没有新数据的情况下重复频繁的拉数据。 如果拉到空数据，则多推后这个时间
  /** backoff time between retries during rebalance */
  // rebalance.backoff.ms 默认值：2000  在重试reblance之前backoff时间
  val rebalanceBackoffMs = props.getInt("rebalance.backoff.ms", zkSyncTimeMs)

  // rebalance.retries.max  4 rebalance时的最大尝试次数
  
  /** backoff time to refresh the leader of a partition after it loses the current leader */
  /**
   * refresh.leader.backoff.ms  默认值： 200 毫秒
   * 在试图确定某个partition的leader是否失去他的leader地位之前，需要等待的backoff时间
   */
  val refreshLeaderBackoffMs = props.getInt("refresh.leader.backoff.ms", RefreshMetadataBackoffMs)

  /** backoff time to reconnect the offsets channel or to retry offset fetches/commits */
  // offset.channel.backoff.ms 默认值： 1000  
  // 重新连接offsets channel或者是重试失败的offset的fetch/commit请求的backoff时间
  val offsetsChannelBackoffMs = props.getInt("offsets.channel.backoff.ms", OffsetsChannelBackoffMs)
  /** socket timeout to use when reading responses for Offset Fetch/Commit requests. This timeout will also be used for
   *  the ConsumerMetdata requests that are used to query for the offset coordinator. */
  
  // offsets.channel.socket.timeout.ms 默认值： 10000 当读取offset的fetch/commit请求回应的socket 超时限制。
  // 此超时限制是被consumerMetadata请求用来请求offset管理
  val offsetsChannelSocketTimeoutMs = props.getInt("offsets.channel.socket.timeout.ms", OffsetsChannelSocketTimeoutMs)

  /** Retry the offset commit up to this many times on failure. This retry count only applies to offset commits during
    * shut-down. It does not apply to commits from the auto-commit thread. It also does not apply to attempts to query
    * for the offset coordinator before committing offsets. i.e., if a consumer metadata request fails for any reason,
    * it is retried and that retry does not count toward this limit. */
  // offsets.commit.max.retries 默认值：5 重试offset commit的次数。
  // 这个重试只应用于offset commits在shut-down之间。
  val offsetsCommitMaxRetries = props.getInt("offsets.commit.max.retries", OffsetsCommitMaxRetries)

  /** Specify whether offsets should be committed to "zookeeper" (default) or "kafka" */
  // offsets.storage 默认值：  zookeeper  ； 用于存放offsets的地点： zookeeper或者kafka
  val offsetsStorage = props.getString("offsets.storage", OffsetsStorage).toLowerCase

  /** If you are using "kafka" as offsets.storage, you can dual commit offsets to ZooKeeper (in addition to Kafka). This
    * is required during migration from zookeeper-based offset storage to kafka-based offset storage. With respect to any
    * given consumer group, it is safe to turn this off after all instances within that group have been migrated to
    * the new jar that commits offsets to the broker (instead of directly to ZooKeeper). */
  //dual.commit.enabled true  
  // 如果使用“kafka”作为offsets.storage，你可以二次提交offset到zookeeper(还有一次是提交到kafka）。
  // 在zookeeper-based的offset storage到kafka-based的offset storage迁移时，这是必须的。
  // 对任意给定的consumer group来说，比较安全的建议是当完成迁移之后就关闭这个选项
  val dualCommitEnabled = props.getBoolean("dual.commit.enabled", if (offsetsStorage == "kafka") true else false)

  /* what to do if an offset is out of range.
     smallest : automatically reset the offset to the smallest offset
     largest : automatically reset the offset to the largest offset
     anything else: throw exception to the consumer */
  /**
   * auto.offset.reset 默认值： largest 
   * 如果offset出了返回，则 smallest: 自动设置reset到最小的offset.
   * largest : 自动设置offset到最大的offset. 其它值不允许，会抛出异常.
   */
  val autoOffsetReset = props.getString("auto.offset.reset", AutoOffsetReset)

  /** throw a timeout exception to the consumer if no message is available for consumption after the specified interval 
   *   consumer.timeout.ms  -1  默认-1,
   *   consumer在没有新消息时无限期的block。如果设置一个正值， 一个超时异常会抛出
   *  */
  val consumerTimeoutMs = props.getInt("consumer.timeout.ms", ConsumerTimeoutMs)

  /**
   * Client id is specified by the kafka consumer client, used to distinguish different clients
   */
  // client.id默认值：  group id value  
  // 是用户特定的字符串，用来在每次请求中帮助跟踪调用。它应该可以逻辑上确认产生这个请求的应用
  val clientId = props.getString("client.id", groupId)

  /** Whether messages from internal topics (such as offsets) should be exposed to the consumer. */
  // exclude.internal.topics : true  ;  是否将内部topics的消息暴露给consumer
  val excludeInternalTopics = props.getBoolean("exclude.internal.topics", ExcludeInternalTopics)

  /** Select a strategy for assigning partitions to consumer streams. Possible values: range, roundrobin */
  // partition.assignment.strategy : 默认值： range
  // 选择向consumer 流分配partitions的策略，可选值：range(排序)，roundrobin(轮询调度算法)
  /**
   * partition.assignment.strategy：  range
   * 在“range”和“roundrobin”策略之间选择一种作为分配partitions给consumer 数据流的策略；
   *  循环的partition分配器分配所有可用的partitions以及所有可用consumer 线程。
   *  它会将partition循环的分配到consumer线程上。
   *  如果所有consumer实例的订阅都是确定的，则partitions的划分是确定的分布。
   *  循环分配策略只有在以下条件满足时才可以：
   *  （1）每个topic在每个consumer实力上都有同样数量的数据流。
   *  （2）订阅的topic的集合对于consumer group中每个consumer实例来说都是确定的。

   */
  val partitionAssignmentStrategy = props.getString("partition.assignment.strategy", DefaultPartitionAssignmentStrategy)
  
  validate(this)
}

