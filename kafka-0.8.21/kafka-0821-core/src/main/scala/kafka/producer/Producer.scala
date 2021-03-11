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

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import kafka.common.{AppInfo, QueueFullException}
import kafka.metrics._
import kafka.producer.async.{DefaultEventHandler, EventHandler, ProducerSendThread}
import kafka.serializer.Encoder
import kafka.utils._


class Producer[K,V](val config: ProducerConfig,
                    private val eventHandler: EventHandler[K,V])  // only for unit testing
  extends Logging {

  private val hasShutdown = new AtomicBoolean(false)
  /**
   * LinkedBlockingQueue是一个单向链表实现的阻塞队列，先进先出的顺序。支持多线程并发操作。
              相比于数组实现的ArrayBlockingQueue的有界，LinkedBlockingQueue可认为是无界队列。多用于任务队列。
     LinkedBlockingQueue继承AbstractQueue，实现了BlockingQueue，Serializable接口。内部使用单向链表存储数据。
              默认初始化容量是Integer最大值。
              插入和取出使用不同的锁，putLock插入锁，takeLock取出锁，添加和删除数据的时候可以并行。
              多CPU情况下可以同一时刻既消费又生产。
   */
  private val queue = new LinkedBlockingQueue[KeyedMessage[K,V]](config.queueBufferingMaxMessages)

  private var sync: Boolean = true
  private var producerSendThread: ProducerSendThread[K,V] = null
  private val lock = new Object()

  config.producerType match {
    case "sync" =>
    case "async" =>
      sync = false
      producerSendThread = new ProducerSendThread[K,V]("ProducerSendThread-" + config.clientId,
                                                       queue,
                                                       eventHandler,
                                                       config.queueBufferingMaxMs,
                                                       config.batchNumMessages,
                                                       config.clientId)
      producerSendThread.start()
  }

  private val producerTopicStats = ProducerTopicStatsRegistry.getProducerTopicStats(config.clientId)

  KafkaMetricsReporter.startReporters(config.props)
  AppInfo.registerInfo()

    /**
   * Producer:当new Producer(new ProducerConfig()),其底层实现，
   * 实际会产生两个核心类的实例：Producer、DefaultEventHandler。
   * 在创建的同时，会默认new一个ProducerPool，即我们每new一个java的Producer类，
   * 就会有创建Producer、EventHandler和ProducerPool，
   * ProducerPool为连接不同kafka broker的池，
   * 初始连接个数有broker.list参数决定。
   * 
   */
  def this(config: ProducerConfig) =
    this(config,new DefaultEventHandler[K,V](config,
                                      Utils.createObject[Partitioner](config.partitionerClass, config.props),
                                      Utils.createObject[Encoder[V]](config.serializerClass, config.props),
                                      Utils.createObject[Encoder[K]](config.keySerializerClass, config.props),
                                      new ProducerPool(config)))

  /**
   * Sends the data, partitioned by key to the topic using either the
   * synchronous or the asynchronous producer
   * @param messages the producer data object that encapsulates the topic, key and message data
   */
  /**
   * 调用producer.send方法流程：
              当应用程序调用producer.send方法时，其内部其实调的是eventhandler.handle(message)方法,eventHandler会首先序列化该消息,
     eventHandler.serialize(events)-->dispatchSerializedData()-->partitionAndCollate()-->
     send()-->SyncProducer.send()
              调用逻辑解释：当客户端应用程序调用producer发送消息messages时(既可以发送单条消息，也可以发送List多条消息)，
              调用eventhandler.serialize首先序列化所有消息，序列化操作用户可以自定义实现Encoder接口，
              下一步调用partitionAndCollate根据topics的messages进行分组操作，
     messages分配给dataPerBroker(多个不同的Broker的Map)，根据不同Broker调用不同的SyncProducer.send批量发送消息数据，
     SyncProducer包装了nio网络操作信息。
   */
  def send(messages: KeyedMessage[K,V]*) {
    lock synchronized {// 同步代码块
      if (hasShutdown.get)
        throw new ProducerClosedException
      recordStats(messages)
      sync match {
        case true => eventHandler.handle(messages) // 默认是同步操作
        case false => asyncSend(messages) // 异步发送
      }
    }
  }

  private def recordStats(messages: Seq[KeyedMessage[K,V]]) {
    for (message <- messages) {
      producerTopicStats.getProducerTopicStats(message.topic).messageRate.mark()
      producerTopicStats.getProducerAllTopicsStats.messageRate.mark()
    }
  }

  // 异步发送信息
  private def asyncSend(messages: Seq[KeyedMessage[K,V]]) {
    for (message <- messages) {
      val added = config.queueEnqueueTimeoutMs match {
        case 0  =>
          queue.offer(message)
        case _  =>
          try {
            config.queueEnqueueTimeoutMs < 0 match {
            case true =>
              queue.put(message)
              true
            case _ =>
              queue.offer(message, config.queueEnqueueTimeoutMs, TimeUnit.MILLISECONDS)
            }
          }
          catch {
            case e: InterruptedException =>
              false
          }
      }
      if(!added) {
        producerTopicStats.getProducerTopicStats(message.topic).droppedMessageRate.mark()
        producerTopicStats.getProducerAllTopicsStats.droppedMessageRate.mark()
        throw new QueueFullException("Event queue is full of unsent messages, could not send event: " + message.toString)
      }else {
        trace("Added to send queue an event: " + message.toString)
        trace("Remaining queue size: " + queue.remainingCapacity)
      }
    }
  }

  /**
   * Close API to close the producer pool connections to all Kafka brokers. Also closes
   * the zookeeper client connection if one exists
   */
  def close() = {
    lock synchronized {
      val canShutdown = hasShutdown.compareAndSet(false, true)
      if(canShutdown) {
        info("Shutting down producer")
        val startTime = System.nanoTime()
        KafkaMetricsGroup.removeAllProducerMetrics(config.clientId)
        if (producerSendThread != null)
          producerSendThread.shutdown
        eventHandler.close
        info("Producer shutdown completed in " + (System.nanoTime() - startTime) / 1000000 + " ms")
      }
    }
  }
}


