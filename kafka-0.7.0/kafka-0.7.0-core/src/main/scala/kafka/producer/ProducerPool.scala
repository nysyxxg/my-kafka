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

package kafka.producer

import async._
import java.util.Properties
import kafka.serializer.Encoder
import org.apache.log4j.Logger
import java.util.concurrent.{ConcurrentMap, ConcurrentHashMap}
import kafka.cluster.{Partition, Broker}
import kafka.api.ProducerRequest
import kafka.common.{UnavailableProducerException, InvalidConfigException}
import kafka.utils.Utils
import kafka.message.{NoCompressionCodec, ByteBufferMessageSet}

class ProducerPool[V](private val config: ProducerConfig,
                      private val serializer: Encoder[V],
                      private val syncProducers: ConcurrentMap[Int, SyncProducer],  // SyncProducer同步发送器
                      private val asyncProducers: ConcurrentMap[Int, AsyncProducer[V]], // AsyncProducer异步发送器
                      private val inputEventHandler: EventHandler[V] = null,
                      private val cbkHandler: CallbackHandler[V] = null) {

  private val logger = Logger.getLogger(classOf[ProducerPool[V]])
  private var eventHandler = inputEventHandler
  if (eventHandler == null){
    eventHandler = new DefaultEventHandler(config, cbkHandler)  // 初始化默认的事件处理器
  }
  if (serializer == null)
    throw new InvalidConfigException("serializer passed in is null!")

  private var sync: Boolean = true
  config.producerType match {
    case "sync" => sync = true
    case "async" => sync = false
    case _ => throw new InvalidConfigException("Valid values for producer.type are sync/async")
  }

  def this(config: ProducerConfig, serializer: Encoder[V],
           eventHandler: EventHandler[V], cbkHandler: CallbackHandler[V]) =
    this(config, serializer,
      new ConcurrentHashMap[Int, SyncProducer](),
      new ConcurrentHashMap[Int, AsyncProducer[V]](),
      eventHandler, cbkHandler)

  def this(config: ProducerConfig, serializer: Encoder[V]) = this(config, serializer,
    new ConcurrentHashMap[Int, SyncProducer](),
    new ConcurrentHashMap[Int, AsyncProducer[V]](),
    Utils.getObject(config.eventHandler),
    Utils.getObject(config.cbkHandler))

  /**
    * add a new producer, either synchronous or asynchronous, connecting  to the specified broker
    * 添加一个新的Producer，异步或者同步，去连接一个特定的broker
    * @param bid  the id of the broker
    * @param host the hostname of the broker
    * @param port the port of the broker
    */
  def addProducer(broker: Broker) {
    val props = new Properties()
    props.put("host", broker.host)
    props.put("port", broker.port.toString)
    props.putAll(config.props)
    if (sync) { //  同步
      val producer = new SyncProducer(new SyncProducerConfig(props)) // 创建同步 Producer
      logger.info("Creating sync producer for broker id = " + broker.id + " at " + broker.host + ":" + broker.port)
      syncProducers.put(broker.id, producer) // 将创建的producer放入缓存中
    } else {
      val producer = new AsyncProducer[V](new AsyncProducerConfig(props),
        new SyncProducer(new SyncProducerConfig(props)),
        serializer,
        eventHandler, config.eventHandlerProps,
        cbkHandler, config.cbkHandlerProps) //  实例化异步发送对象AsyncProducer
      producer.start // 启动单独一个发送线程
      logger.info("Creating async producer for broker id = " + broker.id + " at " + broker.host + ":" + broker.port)
      asyncProducers.put(broker.id, producer)
    }
  }

  /**
    * selects either a synchronous or an asynchronous producer, for
    * the specified broker id and calls the send API on the selected
    * producer to publish the data to the specified broker partition
    *
    * @param poolData the producer pool request object
    */
  def send(poolData: ProducerPoolData[V]*) {
    println("----------ProducerPool--------send-----------start------")
    val distinctBrokers = poolData.map(pd => pd.getBidPid.brokerId).distinct  // 得到所有的broker的id
    var remainingRequests = poolData.toSeq
    distinctBrokers.foreach { bid =>  // 开始遍历每一个brokerid
      // 对集合进行分组 ,将同bid一个broker的数据，分为同一个分区, 如果不同的bid，就分配到另外一个分区中
      val requestsForThisBid: (Seq[ProducerPoolData[V]], Seq[ProducerPoolData[V]]) = remainingRequests partition (_.getBidPid.brokerId == bid)
      remainingRequests = requestsForThisBid._2 // 剩余不相同的bid数据

      if (sync) { // 如果是同步发送
        println("----------ProducerPool---------遍历要发送给 broker 为 bid 消息集合.............." )
        val producerRequests = requestsForThisBid._1.map(req => {  // 开始遍历每一个消息集合
          val messages = req.getData.map(d => serializer.toMessage(d)) // 对一条消息，进行序列化
          // 实例化ByteBufferMessageSet 对象
          val byteBufferMessageSet = new ByteBufferMessageSet(compressionCodec = config.compressionCodec, messages: _*) // 构建消息集合
          new ProducerRequest(req.getTopic, req.getBidPid.partId, byteBufferMessageSet) // 构建发送请求对象
        })
        logger.debug("Fetching sync producer for broker id: " + bid)
        val producer = syncProducers.get(bid)  // 根据bid，获取对应的发送者对象 producer
        if (producer != null) {
          if (producerRequests.size > 1){ // 如果大于1，就进行批量发送
            println("----------ProducerPool--------send-----------批量发送-----size=-"+ producerRequests.size)
            producer.multiSend(producerRequests.toArray)
          }else { // 如果等于1，就单一发送
            println("----------ProducerPool--------send-----------单条发送------size="+ producerRequests.size)
            producer.send(topic = producerRequests(0).topic, partition = producerRequests(0).partition, messages = producerRequests(0).messages)
          }
          config.compressionCodec match {
            case NoCompressionCodec => logger.debug("Sending message to broker " + bid)
            case _ => logger.debug("Sending compressed messages to broker " + bid)
          }
        } else {
          throw new UnavailableProducerException("Producer pool has not been initialized correctly. " + "Sync Producer for broker " + bid + " does not exist in the pool")
        }
      } else {  // 如果是异步发送
        logger.debug("Fetching async producer for broker id: " + bid)
        val producer = asyncProducers.get(bid)
        if (producer != null) {
          requestsForThisBid._1.foreach { req =>
            req.getData.foreach(d => producer.send(req.getTopic, d, req.getBidPid.partId))
          }
          if (logger.isDebugEnabled)
            config.compressionCodec match {
              case NoCompressionCodec => logger.debug("Sending message")
              case _ => logger.debug("Sending compressed messages")
            }
        }
        else {
          throw new UnavailableProducerException("Producer pool has not been initialized correctly. " + "Async Producer for broker " + bid + " does not exist in the pool")
        }
      }
    }
  }

  /**
    * Closes all the producers in the pool
    */
  def close() = {
    config.producerType match {
      case "sync" =>
        logger.info("Closing all sync producers")
        val iter = syncProducers.values.iterator
        while (iter.hasNext)
          iter.next.close
      case "async" =>
        logger.info("Closing all async producers")
        val iter = asyncProducers.values.iterator
        while (iter.hasNext)
          iter.next.close
    }
  }

  /**
    * This constructs and returns the request object for the producer pool
    *
    * @param topic  the topic to which the data should be published
    * @param bidPid the broker id and partition id
    * @param data   the data to be published
    */
  def getProducerPoolData(topic: String, bidPid: Partition, data: Seq[V]): ProducerPoolData[V] = {
    new ProducerPoolData[V](topic, bidPid, data)
  }

  class ProducerPoolData[V](topic: String,
                            bidPid: Partition,
                            data: Seq[V]) {
    def getTopic: String = topic

    def getBidPid: Partition = bidPid

    def getData: Seq[V] = data
  }

}
