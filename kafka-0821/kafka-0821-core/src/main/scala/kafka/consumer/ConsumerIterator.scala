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

import kafka.utils.{IteratorTemplate, Logging, Utils}
import java.util.concurrent.{TimeUnit, BlockingQueue}
import kafka.serializer.Decoder
import java.util.concurrent.atomic.AtomicReference
import kafka.message.{MessageAndOffset, MessageAndMetadata}
import kafka.common.{KafkaException, MessageSizeTooLargeException}


/**
 * An iterator that blocks until a value can be read from the supplied queue.
 * The iterator takes a shutdownCommand object which can be added to the queue to trigger a shutdown
 *
 */
class ConsumerIterator[K, V](private val channel: BlockingQueue[FetchedDataChunk],
                             consumerTimeoutMs: Int,
                             private val keyDecoder: Decoder[K],
                             private val valueDecoder: Decoder[V],
                             val clientId: String)
  extends IteratorTemplate[MessageAndMetadata[K, V]] with Logging {

  private var current: AtomicReference[Iterator[MessageAndOffset]] = new AtomicReference(null)
  private var currentTopicInfo: PartitionTopicInfo = null
  private var consumedOffset: Long = -1L
  private val consumerTopicStats = ConsumerTopicStatsRegistry.getConsumerTopicStat(clientId)

  override def next(): MessageAndMetadata[K, V] = {
    val item = super.next()
    if(consumedOffset < 0)
      throw new KafkaException("Offset returned by the message set is invalid %d".format(consumedOffset))
      // 使用makeNext方法设置的consumedOffset，去修改topicInfo的消费offset  
    currentTopicInfo.resetConsumeOffset(consumedOffset)
    val topic = currentTopicInfo.topic
    trace("Setting %s consumed offset to %d".format(topic, consumedOffset))
    consumerTopicStats.getConsumerTopicStats(topic).messageRate.mark()
    consumerTopicStats.getConsumerAllTopicStats().messageRate.mark()
    // 返回makeNext得到的item  
    item
  }

  /**
   *  客户端用ConsumerIterator不断的从分区信息的内部队列中取数据。ConsumerIterator实现了IteratorTemplate的接口，
                它的内部保存一个Iterator的属性current，每次调用makeNext时会检查它，如果有则从中取否则从队列中取。
   */
  protected def makeNext(): MessageAndMetadata[K, V] = {
    var currentDataChunk: FetchedDataChunk = null
    // if we don't have an iterator, get one  ，从内部变量中取数据  
    var localCurrent = current.get()
    if(localCurrent == null || !localCurrent.hasNext) {
      if (consumerTimeoutMs < 0)// 内部变量中取不到值，检查timeout的值  
        currentDataChunk = channel.take // 是负数(-1)，则表示永不过期，如果接下来无新数据可取，客户端线程会在channel.take阻塞住  
      else {// 设置了过期时间，在没有新数据可用时，pool会在相应的时间返回，返回值为空，则说明没有取到新数据，抛出timeout的异常  
        currentDataChunk = channel.poll(consumerTimeoutMs, TimeUnit.MILLISECONDS)
        if (currentDataChunk == null) {
          // reset state to make the iterator re-iterable
          resetState()
          throw new ConsumerTimeoutException
        }
      }
      // kafka把shutdown的命令也做为一个datachunk放到队列中，用这种方法来保证消息的顺序性  
      if(currentDataChunk eq ZookeeperConsumerConnector.shutdownCommand) {
        debug("Received the shutdown command")
        return allDone
      } else {
        currentTopicInfo = currentDataChunk.topicInfo
        val cdcFetchOffset = currentDataChunk.fetchOffset
        val ctiConsumeOffset = currentTopicInfo.getConsumeOffset
        if (ctiConsumeOffset < cdcFetchOffset) {
          error("consumed offset: %d doesn't match fetch offset: %d for %s;\n Consumer may lose data"
            .format(ctiConsumeOffset, cdcFetchOffset, currentTopicInfo))
          currentTopicInfo.resetConsumeOffset(cdcFetchOffset)
        }// 把取出chunk中的消息转化为iterator  
        localCurrent = currentDataChunk.messages.iterator
// 使用这个新的iterator初始化current，下次可直接从current中取数据 
        current.set(localCurrent)
      }
      // if we just updated the current chunk and it is empty that means the fetch size is too small!
      if(currentDataChunk.messages.validBytes == 0)
        throw new MessageSizeTooLargeException("Found a message larger than the maximum fetch size of this consumer on topic " +
                                               "%s partition %d at fetch offset %d. Increase the fetch size, or decrease the maximum message size the broker will allow."
                                               .format(currentDataChunk.topicInfo.topic, currentDataChunk.topicInfo.partitionId, currentDataChunk.fetchOffset))
    }
    // 取出下一条数据，并用下一条数据的offset值设置consumedOffset  
    var item = localCurrent.next()
    // reject the messages that have already been consumed
    while (item.offset < currentTopicInfo.getConsumeOffset && localCurrent.hasNext) {
      item = localCurrent.next()
    }
    consumedOffset = item.nextOffset

    item.message.ensureValid() // validate checksum of message to ensure it is valid
// 解码消息，封装消息和它的topic信息到MessageAndMetadata对象，返回  
    new MessageAndMetadata(currentTopicInfo.topic, currentTopicInfo.partitionId, item.message, item.offset, keyDecoder, valueDecoder)
  }

  def clearCurrentChunk() {
    try {
      debug("Clearing the current data chunk for this consumer iterator")
      current.set(null)
    }
  }
}

class ConsumerTimeoutException() extends RuntimeException()

