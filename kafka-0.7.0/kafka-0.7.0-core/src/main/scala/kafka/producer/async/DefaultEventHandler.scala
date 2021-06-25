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

package kafka.producer.async

import collection.mutable.HashMap
import collection.mutable.Map
import org.apache.log4j.Logger
import kafka.api.ProducerRequest
import kafka.serializer.Encoder
import java.util.Properties
import kafka.producer.{ProducerConfig, SyncProducer}
import kafka.message.{NoCompressionCodec, ByteBufferMessageSet}


private[kafka] class DefaultEventHandler[T](val config: ProducerConfig,
                                            val cbkHandler: CallbackHandler[T]) extends EventHandler[T] {

  private val logger = Logger.getLogger(classOf[DefaultEventHandler[T]])

  override def init(props: Properties) {}

  // 处理事件
  override def handle(events: Seq[QueueItem[T]], syncProducer: SyncProducer, serializer: Encoder[T]) {
    println("-------------DefaultEventHandler-------------------------handle--------------------------")
    var processedEvents = events
    if (cbkHandler != null) {
      processedEvents = cbkHandler.beforeSendingData(events)
    }
    if (logger.isTraceEnabled) {
      processedEvents.foreach(event => logger.trace("Handling event for Topic: %s, Partition: %d"
        .format(event.getTopic, event.getPartition)))
    }
    val serializeData = serialize(collate(processedEvents), serializer) // 序列化数据，转化为ByteBufferMessageSet
    send(serializeData, syncProducer) // 发送数据
  }

  private def send(messagesPerTopic: Map[(String, Int), ByteBufferMessageSet], syncProducer: SyncProducer) {
    println("-------------DefaultEventHandler-------------------------send--------------------------")
    if (messagesPerTopic.size > 0) {
      val requests = messagesPerTopic.map(f => new ProducerRequest(f._1._1, f._1._2, f._2)).toArray  // 将发送的消息，转化为ProducerRequest发送请求
      syncProducer.multiSend(requests) // 批量发送
      if (logger.isTraceEnabled)
        logger.trace("kafka producer sent messages for topics " + messagesPerTopic)
    }
  }

  private def serialize(eventsPerTopic: Map[(String, Int), Seq[T]],
                        serializer: Encoder[T]): Map[(String, Int), ByteBufferMessageSet] = {
    val eventsPerTopicMap = eventsPerTopic.map(e => ((e._1._1, e._1._2), e._2.map(l => serializer.toMessage(l)))) // 转化为 Message
    /** enforce the compressed.topics config here.
      * If the compression codec is anything other than NoCompressionCodec,
      * Enable compression only for specified topics if any
      * If the list of compressed topics is empty, then enable the specified compression codec for all topics
      * If the compression codec is NoCompressionCodec, compression is disabled for all topics
      */

    val messagesPerTopicPartition = eventsPerTopicMap.map { topicAndEvents =>
      ((topicAndEvents._1._1, topicAndEvents._1._2),
        config.compressionCodec match {
          case NoCompressionCodec => // 如果没有压缩
            if (logger.isTraceEnabled)
              logger.trace("Sending %d messages with no compression to topic %s on partition %d"
                .format(topicAndEvents._2.size, topicAndEvents._1._1, topicAndEvents._1._2))
            new ByteBufferMessageSet(NoCompressionCodec, topicAndEvents._2: _*) // 将Message 封装为  ByteBufferMessageSet
          case _ =>
            config.compressedTopics.size match {
              case 0 =>
                if (logger.isTraceEnabled)
                  logger.trace("Sending %d messages with compression codec %d to topic %s on partition %d"
                    .format(topicAndEvents._2.size, config.compressionCodec.codec, topicAndEvents._1._1, topicAndEvents._1._2))
                new ByteBufferMessageSet(config.compressionCodec, topicAndEvents._2: _*)
              case _ =>
                if (config.compressedTopics.contains(topicAndEvents._1._1)) {
                  if (logger.isTraceEnabled)
                    logger.trace("Sending %d messages with compression codec %d to topic %s on partition %d"
                      .format(topicAndEvents._2.size, config.compressionCodec.codec, topicAndEvents._1._1, topicAndEvents._1._2))
                  new ByteBufferMessageSet(config.compressionCodec, topicAndEvents._2: _*)
                }
                else {
                  if (logger.isTraceEnabled)
                    logger.trace("Sending %d messages to topic %s and partition %d with no compression as %s is not in compressed.topics - %s"
                      .format(topicAndEvents._2.size, topicAndEvents._1._1, topicAndEvents._1._2, topicAndEvents._1._1,
                        config.compressedTopics.toString))
                  new ByteBufferMessageSet(NoCompressionCodec, topicAndEvents._2: _*)
                }
            }
        })
    }
    messagesPerTopicPartition
  }

  //  对每一条event对象进行转换
  private def collate(events: Seq[QueueItem[T]]): Map[(String, Int), Seq[T]] = {
    val collatedEvents = new HashMap[(String, Int), Seq[T]] // [(topic,ppartition),数据集合]
    val distinctTopics = events.map(e => e.getTopic).toSeq.distinct // 获取event中所有的topic
    val distinctPartitions = events.map(e => e.getPartition).distinct // 获取event中所有的partition

    var remainingEvents = events
    distinctTopics foreach { topic => // 开始遍历所有的topic
      val topicEvents = remainingEvents partition (e => e.getTopic.equals(topic)) // 对所有的envents 进行分区
      remainingEvents = topicEvents._2 // 不满足条件的数据
      distinctPartitions.foreach { p => // 遍历event中所有的分区
        val topicPartitionEvents = (topicEvents._1 partition (e => (e.getPartition == p)))._1 // 得到相同topic的相同分区的events对象集合
        if (topicPartitionEvents.size > 0) { // 如果集合对象大于0
          collatedEvents += ((topic, p) -> topicPartitionEvents.map(q => q.getData)) //相同topic的相同分区的--->events对象集合
        }
      }
    }
    collatedEvents
  }

  override def close = {
  }
}
