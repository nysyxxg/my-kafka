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

package kafka.producer

import async.AsyncProducerConfig
import java.util.Properties
import kafka.utils.{Utils, VerifiableProperties}
import kafka.message.{CompressionCodec, NoCompressionCodec}
import kafka.common.{InvalidConfigException, Config}

object ProducerConfig extends Config {
  def validate(config: ProducerConfig) {
    validateClientId(config.clientId)
    validateBatchSize(config.batchNumMessages, config.queueBufferingMaxMessages)
    validateProducerType(config.producerType)
  }

  def validateClientId(clientId: String) {
    validateChars("client.id", clientId)
  }

  def validateBatchSize(batchSize: Int, queueSize: Int) {
    if (batchSize > queueSize)
      throw new InvalidConfigException("Batch size = " + batchSize + " can't be larger than queue size = " + queueSize)
  }

  def validateProducerType(producerType: String) {
    producerType match {
      case "sync" =>
      case "async"=>
      case _ => throw new InvalidConfigException("Invalid value " + producerType + " for producer.type, valid values are sync/async")
    }
  }
}

class ProducerConfig private (val props: VerifiableProperties)
        extends AsyncProducerConfig with SyncProducerConfigShared {
  import ProducerConfig._

  def this(originalProps: Properties) {
    this(new VerifiableProperties(originalProps))
    props.verify()
  }

  /** This is for bootstrapping and the producer will only use it for getting metadata
   * (topics, partitions and replicas). The socket connections for sending the actual data
   * will be established based on the broker information returned in the metadata. The
   * format is host1:port1,host2:port2, and the list can be a subset of brokers or
   * a VIP pointing to a subset of brokers.
   * boker list 
   * 使用这个参数传入boker和分区的静态信息，如host1:port1,host2:port2, 这个可以是全部boker的一部分
   * 
   */
  val brokerList = props.getString("metadata.broker.list")

  /** the partitioner class for partitioning events amongst sub-topics */
  val partitionerClass = props.getString("partitioner.class", "kafka.producer.DefaultPartitioner")

  /** this parameter specifies whether the messages are sent asynchronously *
   * or not. Valid values are - async for asynchronous send                 *
   *                            sync for synchronous send                   */
  /**
   *  默认值： sync
   *  指定消息发送是同步还是异步。异步asyc成批发送用kafka.producer.AyncProducer， 
   *  同步sync用kafka.producer.SyncProducer
   * 
   */
  val producerType = props.getString("producer.type", "sync")

  /**
   * This parameter allows you to specify the compression codec for all data generated *
   * by this producer. The default is NoCompressionCodec
   * 消息压缩，默认不压缩
   */
  val compressionCodec = props.getCompressionCodec("compression.codec", NoCompressionCodec)

  /** This parameter allows you to set whether compression should be turned *
   *  on for particular topics
   *
   *  If the compression codec is anything other than NoCompressionCodec,
   *
   *    Enable compression only for specified topics if any
   *
   *    If the list of compressed topics is empty, then enable the specified compression codec for all topics
   *
   *  If the compression codec is NoCompressionCodec, compression is disabled for all topics
   *  在设置了压缩的情况下，可以指定特定的topic压缩，未指定则全部压缩
   *  compressed.topics 默认值： null
   */
  val compressedTopics = Utils.parseCsvList(props.getString("compressed.topics", null))

  /** The leader may be unavailable transiently, which can fail the sending of a message.
    *  This property specifies the number of retries when such failures occur.
    *  消息发送最大尝试次数
    */
  val messageSendMaxRetries = props.getInt("message.send.max.retries", 3)

  /** Before each retry, the producer refreshes the metadata of relevant topics. Since leader
    * election takes a bit of time, this property specifies the amount of time that the producer
    * waits before refreshing the metadata.
    * 每次尝试增加的额外的间隔时间 ： 默认值：100
    */
  val retryBackoffMs = props.getInt("retry.backoff.ms", 100)

  /**
   * The producer generally refreshes the topic metadata from brokers when there is a failure
   * (partition missing, leader not available...). It will also poll regularly (default: every 10min
   * so 600000ms). If you set this to a negative value, metadata will only get refreshed on failure.
   * If you set this to zero, the metadata will get refreshed after each message sent (not recommended)
   * Important note: the refresh happen only AFTER the message is sent, so if the producer never sends
   * a message the metadata is never refreshed
   * 
   * topic.metadata.refresh.interval.ms 默認值： 600000  
            定期的获取元数据的时间。当分区丢失，leader不可用时producer也会主动获取元数据，如果为0，则每次发送完消息就获取元数据，
            不推荐。如果为负值，则只有在失败的情况下获取元数据。

   */
  val topicMetadataRefreshIntervalMs = props.getInt("topic.metadata.refresh.interval.ms", 600000)

  validate(this)
}
