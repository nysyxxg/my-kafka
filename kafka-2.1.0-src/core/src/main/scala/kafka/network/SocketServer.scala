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

package kafka.network

import java.io.IOException
import java.net._
import java.nio.channels._
import java.nio.channels.{Selector => NSelector}
import java.util.concurrent._
import java.util.concurrent.atomic._

import com.yammer.metrics.core.Gauge
import kafka.cluster.{BrokerEndPoint, EndPoint}
import kafka.metrics.KafkaMetricsGroup
import kafka.network.RequestChannel.{CloseConnectionResponse, EndThrottlingResponse, NoOpResponse, SendResponse, StartThrottlingResponse}
import kafka.security.CredentialProvider
import kafka.server.KafkaConfig
import kafka.utils._
import org.apache.kafka.common.{KafkaException, Reconfigurable}
import org.apache.kafka.common.memory.{MemoryPool, SimpleMemoryPool}
import org.apache.kafka.common.metrics._
import org.apache.kafka.common.metrics.stats.Meter
import org.apache.kafka.common.network.KafkaChannel.ChannelMuteEvent
import org.apache.kafka.common.network.{ChannelBuilder, ChannelBuilders, KafkaChannel, ListenerName, Selectable, Send, Selector => KSelector}
import org.apache.kafka.common.requests.{RequestContext, RequestHeader}
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.utils.{KafkaThread, LogContext, Time}
import org.slf4j.event.Level

import scala.collection._
import JavaConverters._
import scala.collection.mutable.{ArrayBuffer, Buffer}
import scala.util.control.ControlThrowable

/**
 * An NIO socket server. The threading model is
 *   1 Acceptor thread that handles new connections
 *   Acceptor has N Processor threads that each have their own selector and read requests from sockets
 *   M Handler threads that handle requests and produce responses back to the processor threads for writing.
 */
class SocketServer(val config: KafkaConfig, val metrics: Metrics, val time: Time, val credentialProvider: CredentialProvider) extends Logging with KafkaMetricsGroup {

  private val maxQueuedRequests = config.queuedMaxRequests

  private val logContext = new LogContext(s"[SocketServer brokerId=${config.brokerId}] ")
  this.logIdent = logContext.logPrefix

  private val memoryPoolSensor = metrics.sensor("MemoryPoolUtilization")
  private val memoryPoolDepletedPercentMetricName = metrics.metricName("MemoryPoolAvgDepletedPercent", "socket-server-metrics")
  private val memoryPoolDepletedTimeMetricName = metrics.metricName("MemoryPoolDepletedTimeTotal", "socket-server-metrics")
  memoryPoolSensor.add(new Meter(TimeUnit.MILLISECONDS, memoryPoolDepletedPercentMetricName, memoryPoolDepletedTimeMetricName))
  private val memoryPool = if (config.queuedMaxBytes > 0) new SimpleMemoryPool(config.queuedMaxBytes, config.socketRequestMaxBytes, false, memoryPoolSensor) else MemoryPool.NONE
  val requestChannel = new RequestChannel(maxQueuedRequests)
  private val processors = new ConcurrentHashMap[Int, Processor]()
  private var nextProcessorId = 0

  private[network] val acceptors = new ConcurrentHashMap[EndPoint, Acceptor]()
 //控制连接数类
  private var connectionQuotas: ConnectionQuotas = _
  private var stoppedProcessingRequests = false

  /**
   * Start the socket server. Acceptors for all the listeners are started. Processors
   * are started if `startupProcessors` is true. If not, processors are only started when
   * [[kafka.network.SocketServer#startProcessors()]] is invoked. Delayed starting of processors
   * is used to delay processing client connections until server is fully initialized, e.g.
   * to ensure that all credentials have been loaded before authentications are performed.
   * Acceptors are always started during `startup` so that the bound port is known when this
   * method completes even when ephemeral ports are used. Incoming connections on this server
   * are processed when processors start up and invoke [[org.apache.kafka.common.network.Selector#poll]].
   *
   * @param startupProcessors Flag indicating whether `Processor`s must be started.
   */
  def startup(startupProcessors: Boolean = true) {
    this.synchronized {
      connectionQuotas = new ConnectionQuotas(config.maxConnectionsPerIp, config.maxConnectionsPerIpOverrides)
      //todo: 创建Acceptor和Processor线程
      createAcceptorAndProcessors(config.numNetworkThreads, config.listeners)
      if (startupProcessors) {
        startProcessors()
      }
    }

    newGauge("NetworkProcessorAvgIdlePercent",
      new Gauge[Double] {

        def value = SocketServer.this.synchronized {
          val ioWaitRatioMetricNames = processors.values.asScala.map { p =>
            metrics.metricName("io-wait-ratio", "socket-server-metrics", p.metricTags)
          }
          ioWaitRatioMetricNames.map { metricName =>
            Option(metrics.metric(metricName)).fold(0.0)(m => Math.min(m.metricValue.asInstanceOf[Double], 1.0))
          }.sum / processors.size
        }
      }
    )
    newGauge("MemoryPoolAvailable",
      new Gauge[Long] {
        def value = memoryPool.availableMemory()
      }
    )
    newGauge("MemoryPoolUsed",
      new Gauge[Long] {
        def value = memoryPool.size() - memoryPool.availableMemory()
      }
    )
    info("Started " + acceptors.size + " acceptor threads")
  }

  /**
   * Starts processors of all the acceptors of this server if they have not already been started.
   * This method is used for delayed starting of processors if [[kafka.network.SocketServer#startup]]
   * was invoked with `startupProcessors=false`.
   */
  def startProcessors(): Unit = synchronized {
    acceptors.values.asScala.foreach { _.startProcessors() }
    info(s"Started processors for ${acceptors.size} acceptors")
  }

  private def endpoints = config.listeners.map(l => l.listenerName -> l).toMap

  private def createAcceptorAndProcessors(processorsPerListener: Int,
                                          endpoints: Seq[EndPoint]): Unit = synchronized {
    //socket发送数据和接受数据的缓冲区
    val sendBufferSize = config.socketSendBufferBytes
    val recvBufferSize = config.socketReceiveBufferBytes
    //broker的节点id
    val brokerId = config.brokerId

    //endpoints: 一台服务器可以配置多个kafka实例
    //node01:9092，node01:9093,node01:9094 ,一般来是一个节点只会配置一个实例
    endpoints.foreach { endpoint =>
      val listenerName = endpoint.listenerName
      val securityProtocol = endpoint.securityProtocol

      // todo:构建一个Acceptor对象,它是一个核心的线程
      // 作用：监听并且接收客户端的请求，同时建立数据传输通道—SocketChannel，然后以轮询的方式交给一个后端的Processor线程处理
      val acceptor = new Acceptor(endpoint, sendBufferSize, recvBufferSize, brokerId, connectionQuotas)
      //todo: 创建Processor线程
      addProcessors(acceptor, endpoint, processorsPerListener)


      //todo：通过KafkaThread来启动acceptor线程
      KafkaThread.nonDaemon(s"kafka-socket-acceptor-$listenerName-$securityProtocol-${endpoint.port}", acceptor).start()
      //todo: 等待acceptor启动完成
      acceptor.awaitStartup()
      //todo: brokerID与acceptor的对应关系存储在map集合acceptors中
      acceptors.put(endpoint, acceptor)
    }
  }

  private def addProcessors(acceptor: Acceptor, endpoint: EndPoint, newProcessorsPerListener: Int): Unit = synchronized {
    //监听器名称
    val listenerName = endpoint.listenerName
    //协议
    val securityProtocol = endpoint.securityProtocol
    //创建一个存储Processor线程的数组
    val listenerProcessors = new ArrayBuffer[Processor]()

    //遍历    newProcessorsPerListener默认为3个(num.network.threads )
    for (_ <- 0 until newProcessorsPerListener) { // 0 1 2
      //构建Processor对象
      val processor = newProcessor(nextProcessorId, connectionQuotas, listenerName, securityProtocol, memoryPool)
      //添加到数组中
      listenerProcessors += processor
      //监控每个Processor线程
      requestChannel.addProcessor(processor)
      //ProcessorId 自增加1
      nextProcessorId += 1
    }
    //把ProcessorId和Processor线程通过map集合存储起来
    listenerProcessors.foreach(p => processors.put(p.id, p))
    //acceptor线程添加listenerProcessors线程池
    acceptor.addProcessors(listenerProcessors)
  }

  /**
    * Stop processing requests and new connections.
    */
  def stopProcessingRequests() = {
    info("Stopping socket server request processors")
    this.synchronized {
      acceptors.asScala.values.foreach(_.shutdown())
      processors.asScala.values.foreach(_.shutdown())
      requestChannel.clear()
      stoppedProcessingRequests = true
    }
    info("Stopped socket server request processors")
  }

  def resizeThreadPool(oldNumNetworkThreads: Int, newNumNetworkThreads: Int): Unit = synchronized {
    info(s"Resizing network thread pool size for each listener from $oldNumNetworkThreads to $newNumNetworkThreads")
    if (newNumNetworkThreads > oldNumNetworkThreads) {
      acceptors.asScala.foreach { case (endpoint, acceptor) =>
        addProcessors(acceptor, endpoint, newNumNetworkThreads - oldNumNetworkThreads)
      }
    } else if (newNumNetworkThreads < oldNumNetworkThreads)
      acceptors.asScala.values.foreach(_.removeProcessors(oldNumNetworkThreads - newNumNetworkThreads, requestChannel))
  }

  /**
    * Shutdown the socket server. If still processing requests, shutdown
    * acceptors and processors first.
    */
  def shutdown() = {
    info("Shutting down socket server")
    this.synchronized {
      if (!stoppedProcessingRequests)
        stopProcessingRequests()
      requestChannel.shutdown()
    }
    info("Shutdown completed")
  }

  def boundPort(listenerName: ListenerName): Int = {
    try {
      acceptors.get(endpoints(listenerName)).serverChannel.socket.getLocalPort
    } catch {
      case e: Exception =>
        throw new KafkaException("Tried to check server's port before server was started or checked for port of non-existing protocol", e)
    }
  }

  def addListeners(listenersAdded: Seq[EndPoint]): Unit = synchronized {
    info(s"Adding listeners for endpoints $listenersAdded")
    createAcceptorAndProcessors(config.numNetworkThreads, listenersAdded)
    startProcessors()
  }

  def removeListeners(listenersRemoved: Seq[EndPoint]): Unit = synchronized {
    info(s"Removing listeners for endpoints $listenersRemoved")
    listenersRemoved.foreach { endpoint =>
      acceptors.asScala.remove(endpoint).foreach(_.shutdown())
    }
  }

  def updateMaxConnectionsPerIp(maxConnectionsPerIp: Int): Unit = {
    info(s"Updating maxConnectionsPerIp: $maxConnectionsPerIp")
    connectionQuotas.updateMaxConnectionsPerIp(maxConnectionsPerIp)
  }

  def updateMaxConnectionsPerIpOverride(maxConnectionsPerIpOverrides: Map[String, Int]): Unit = {
    info(s"Updating maxConnectionsPerIpOverrides: ${maxConnectionsPerIpOverrides.map { case (k, v) => s"$k=$v" }.mkString(",")}")
    connectionQuotas.updateMaxConnectionsPerIpOverride(maxConnectionsPerIpOverrides)
  }

  /* `protected` for test usage */
  protected[network] def newProcessor(id: Int, connectionQuotas: ConnectionQuotas, listenerName: ListenerName,
                                      securityProtocol: SecurityProtocol, memoryPool: MemoryPool): Processor = {
    new Processor(id,
      time,
      config.socketRequestMaxBytes,
      requestChannel,
      connectionQuotas,
      config.connectionsMaxIdleMs,
      config.failedAuthenticationDelayMs,
      listenerName,
      securityProtocol,
      config,
      metrics,
      credentialProvider,
      memoryPool,
      logContext
    )
  }

  /* For test usage */
  private[network] def connectionCount(address: InetAddress): Int =
    Option(connectionQuotas).fold(0)(_.get(address))

  /* For test usage */
  private[network] def processor(index: Int): Processor = processors.get(index)

}

/**
 * A base class with some helper variables and methods
 */
private[kafka] abstract class AbstractServerThread(connectionQuotas: ConnectionQuotas) extends Runnable with Logging {

  private val startupLatch = new CountDownLatch(1)

  // `shutdown()` is invoked before `startupComplete` and `shutdownComplete` if an exception is thrown in the constructor
  // (e.g. if the address is already in use). We want `shutdown` to proceed in such cases, so we first assign an open
  // latch and then replace it in `startupComplete()`.
  @volatile private var shutdownLatch = new CountDownLatch(0)

  private val alive = new AtomicBoolean(true)

  def wakeup(): Unit

  /**
   * Initiates a graceful shutdown by signaling to stop and waiting for the shutdown to complete
   */
  def shutdown(): Unit = {
    if (alive.getAndSet(false))
      wakeup()
    shutdownLatch.await()
  }

  /**
   * Wait for the thread to completely start up
   */
  def awaitStartup(): Unit = startupLatch.await

  /**
   * Record that the thread startup is complete
   */
  protected def startupComplete(): Unit = {
    // Replace the open latch with a closed one
    shutdownLatch = new CountDownLatch(1)
    startupLatch.countDown()
  }

  /**
   * Record that the thread shutdown is complete
   */
  protected def shutdownComplete(): Unit = shutdownLatch.countDown()

  /**
   * Is the server still running?
   */
  protected def isRunning: Boolean = alive.get

  /**
   * Close `channel` and decrement the connection count.
   */
  def close(channel: SocketChannel): Unit = {
    if (channel != null) {
      debug("Closing connection from " + channel.socket.getRemoteSocketAddress())
      connectionQuotas.dec(channel.socket.getInetAddress)
      CoreUtils.swallow(channel.socket().close(), this, Level.ERROR)
      CoreUtils.swallow(channel.close(), this, Level.ERROR)
    }
  }
}

/**
 * Thread that accepts and configures new connections. There is one of these per endpoint.
 */
private[kafka] class Acceptor(val endPoint: EndPoint,
                              val sendBufferSize: Int,
                              val recvBufferSize: Int,
                              brokerId: Int,
                              connectionQuotas: ConnectionQuotas) extends AbstractServerThread(connectionQuotas) with KafkaMetricsGroup {
  //todo: 创建多路复用器
  private val nioSelector = NSelector.open()
  //todo: 构建ServerSocketChannel
  val serverChannel = openServerSocket(endPoint.host, endPoint.port)
  private val processors = new ArrayBuffer[Processor]()
  private val processorsStarted = new AtomicBoolean

  private[network] def addProcessors(newProcessors: Buffer[Processor]): Unit = synchronized {
    // todo:添加一组新的Processor线程
    processors ++= newProcessors
    //todo: 如果Processor线程池已经启动
    if (processorsStarted.get)
    //todo: 启动新的Processor线程
      startProcessors(newProcessors)
  }

  private[network] def startProcessors(): Unit = synchronized {
    if (!processorsStarted.getAndSet(true)) {
      startProcessors(processors)
    }
  }

  private def startProcessors(processors: Seq[Processor]): Unit = synchronized {
    //todo: 遍历依次启动processor线程
    processors.foreach { processor =>
      //todo: 通过KafkaThread来启动每一个processor线程
      KafkaThread.nonDaemon(s"kafka-network-thread-$brokerId-${endPoint.listenerName}-${endPoint.securityProtocol}-${processor.id}",
        processor).start()
    }
  }

  private[network] def removeProcessors(removeCount: Int, requestChannel: RequestChannel): Unit = synchronized {
    // Shutdown `removeCount` processors. Remove them from the processor list first so that no more
    // connections are assigned. Shutdown the removed processors, closing the selector and its connections.
    // The processors are then removed from `requestChannel` and any pending responses to these processors are dropped.
    // 获取Processor线程池中最后removeCount个线程
    val toRemove = processors.takeRight(removeCount)
    // 移除最后removeCount个线程
    processors.remove(processors.size - removeCount, removeCount)
    // 关闭最后removeCount个线程
    toRemove.foreach(_.shutdown())
    // 在RequestChannel中移除这些Processor
    toRemove.foreach(processor => requestChannel.removeProcessor(processor.id))
  }

  override def shutdown(): Unit = {
    super.shutdown()
    synchronized {
      processors.foreach(_.shutdown())
    }
  }

  /**
   * Accept loop that checks for new connection attempts
   */
  def run() {
    /**
      *  Kafka
      *   NIO网络通信
      *     服务端：ServerSocketChannel
      *     客户端：生产者
      *   服务端启动，然后客户端发送请求，服务端接受请求，发送响应给客户端，客户端接受到响应之后再次发送下一个请求
      */
    //todo: 向NIOSelector上注册一个OP_ACCEPT事件，接下来就可以接受客户端请求了
    serverChannel.register(nioSelector, SelectionKey.OP_ACCEPT)
    startupComplete()
    try {
       //计数器
      var currentProcessor = 0
      //todo： 服务一直在循环执行
      while (isRunning) {

        try {
          //todo: 每隔500ms获取注册key的个数
          val ready = nioSelector.select(500)
          if (ready > 0) {
            //todo: 获取到所有注册的key
            val keys = nioSelector.selectedKeys()
            //todo: 迭代keys
            val iter = keys.iterator()
            //遍历
            while (iter.hasNext && isRunning) {
              try {
                //取出每一个key
                val key = iter.next
                //移除key
                iter.remove()
                //todo：是客户端发送过来的建立连接事件的key
                if (key.isAcceptable) {
                  val processor = synchronized {
                    //轮训算法获取processors数组的下标
                    currentProcessor = currentProcessor % processors.size
                    //通过下标获取出对应的Processor线程
                    processors(currentProcessor)
                  }
                  //对key事件进行处理, 使用轮训的方式，来调度Processor线程处理不同的请求
                  accept(key, processor)
                } else
                  throw new IllegalStateException("Unrecognized key state for acceptor thread.")

                // round robin to the next processor thread, mod(numProcessors) will be done later
                //计数器+1
                currentProcessor = currentProcessor + 1
              } catch {
                case e: Throwable => error("Error while accepting connection", e)
              }
            }
          }
        }
        catch {
          // We catch all the throwables to prevent the acceptor thread from exiting on exceptions due
          // to a select operation on a specific channel or a bad request. We don't want
          // the broker to stop responding to requests from other clients in these scenarios.
          case e: ControlThrowable => throw e
          case e: Throwable => error("Error occurred", e)
        }
      }
    } finally {
      debug("Closing server socket and selector.")
      CoreUtils.swallow(serverChannel.close(), this, Level.ERROR)
      CoreUtils.swallow(nioSelector.close(), this, Level.ERROR)
      shutdownComplete()
    }
  }

  /*
   * Create a server socket to listen for connections on.
   */
  private def openServerSocket(host: String, port: Int): ServerSocketChannel = {
    val socketAddress =
      if (host == null || host.trim.isEmpty)
        new InetSocketAddress(port)
      else
        new InetSocketAddress(host, port)
    //todo: 构建ServerSocketChannel
    val serverChannel = ServerSocketChannel.open()
    //设置异步非阻塞
    serverChannel.configureBlocking(false)
    if (recvBufferSize != Selectable.USE_DEFAULT_BUFFER_SIZE)
      serverChannel.socket().setReceiveBufferSize(recvBufferSize)

    try {
      //绑定主机和端口
      serverChannel.socket.bind(socketAddress)
      info("Awaiting socket connections on %s:%d.".format(socketAddress.getHostString, serverChannel.socket.getLocalPort))
    } catch {
      case e: SocketException =>
        throw new KafkaException("Socket server failed to bind to %s:%d: %s.".format(socketAddress.getHostString, port, e.getMessage), e)
    }
    //返回ServerSocketChannel
    serverChannel
  }

  /*
   * Accept a new connection
   */
  def accept(key: SelectionKey, processor: Processor) {
    //todo: 通过key获取对应的ServerSocketChannel
    val serverSocketChannel = key.channel().asInstanceOf[ServerSocketChannel]
    //todo: 获取到SocketChannel
    // 通过SelectionKey取得与之对应的serverSocketChannel实例，并调用它的accept()方法与客户端建立连接；
    val socketChannel = serverSocketChannel.accept()
    try {
      //todo: 调用connectionQuotas.inc()方法增加连接统计计数
      connectionQuotas.inc(socketChannel.socket().getInetAddress)
      //非阻塞模型
      socketChannel.configureBlocking(false)
      socketChannel.socket().setTcpNoDelay(true)
      socketChannel.socket().setKeepAlive(true)
      if (sendBufferSize != Selectable.USE_DEFAULT_BUFFER_SIZE)
        socketChannel.socket().setSendBufferSize(sendBufferSize)

      debug("Accepted connection from %s on %s and assigned it to processor %d, sendBufferSize [actual|requested]: [%d|%d] recvBufferSize [actual|requested]: [%d|%d]"
            .format(socketChannel.socket.getRemoteSocketAddress, socketChannel.socket.getLocalSocketAddress, processor.id,
                  socketChannel.socket.getSendBufferSize, sendBufferSize,
                  socketChannel.socket.getReceiveBufferSize, recvBufferSize))

      //todo: processor调用acceptor方法对socketChannel进行处理
      processor.accept(socketChannel)
    } catch {
      case e: TooManyConnectionsException =>
        info("Rejected connection from %s, address already has the configured maximum of %d connections.".format(e.ip, e.count))
        close(socketChannel)
    }
  }

  /**
   * Wakeup the thread for selection.
   */
  @Override
  def wakeup = nioSelector.wakeup()

}

private[kafka] object Processor {
  val IdlePercentMetricName = "IdlePercent"
  val NetworkProcessorMetricTag = "networkProcessor"
  val ListenerMetricTag = "listener"
}

/**
 * Thread that processes all requests from a single connection. There are N of these running in parallel
 * each of which has its own selector
 */
private[kafka] class Processor(val id: Int,
                               time: Time,
                               maxRequestSize: Int,
                               requestChannel: RequestChannel,
                               connectionQuotas: ConnectionQuotas,
                               connectionsMaxIdleMs: Long,
                               failedAuthenticationDelayMs: Int,
                               listenerName: ListenerName,
                               securityProtocol: SecurityProtocol,
                               config: KafkaConfig,
                               metrics: Metrics,
                               credentialProvider: CredentialProvider,
                               memoryPool: MemoryPool,
                               logContext: LogContext) extends AbstractServerThread(connectionQuotas) with KafkaMetricsGroup {

  import Processor._
  private object ConnectionId {
    def fromString(s: String): Option[ConnectionId] = s.split("-") match {
      case Array(local, remote, index) => BrokerEndPoint.parseHostPort(local).flatMap { case (localHost, localPort) =>
        BrokerEndPoint.parseHostPort(remote).map { case (remoteHost, remotePort) =>
          ConnectionId(localHost, localPort, remoteHost, remotePort, Integer.parseInt(index))
        }
      }
      case _ => None
    }
  }

  private[network] case class ConnectionId(localHost: String, localPort: Int, remoteHost: String, remotePort: Int, index: Int) {
    override def toString: String = s"$localHost:$localPort-$remoteHost:$remotePort-$index"
  }

  //每个Processor线程都会有自己的SocketChannel队列,它保存的是要创建的新连接信息,每当 Processor 线程接收新的连接请求时，都会将对应的 SocketChannel 放入这个队列
  private val newConnections = new ConcurrentLinkedQueue[SocketChannel]()

  // 这是一个临时 Response 队列,当 Processor 线程将 Response 返还给 Request 发送方之后，还要将 Response 放入这个临时队列.
  private val inflightResponses = mutable.Map[String, RequestChannel.Response]()

  //response队列 ,Response 队列里面保存着需要被返还给发送方的所有 Response 对象
  private val responseQueue = new LinkedBlockingDeque[RequestChannel.Response]()

  private[kafka] val metricTags = mutable.LinkedHashMap(
    ListenerMetricTag -> listenerName.value,
    NetworkProcessorMetricTag -> id.toString
  ).asJava

  newGauge(IdlePercentMetricName,
    new Gauge[Double] {
      def value = {
        Option(metrics.metric(metrics.metricName("io-wait-ratio", "socket-server-metrics", metricTags)))
          .fold(0.0)(m => Math.min(m.metricValue.asInstanceOf[Double], 1.0))
      }
    },
    // for compatibility, only add a networkProcessor tag to the Yammer Metrics alias (the equivalent Selector metric
    // also includes the listener name)
    Map(NetworkProcessorMetricTag -> id.toString)
  )

  //每一个Processor线程创建一个Selector
  private val selector = createSelector(
    ChannelBuilders.serverChannelBuilder(listenerName,
      listenerName == config.interBrokerListenerName,
      securityProtocol,
      config,
      credentialProvider.credentialCache,
      credentialProvider.tokenCache))
  // Visible to override for testing
  protected[network] def createSelector(channelBuilder: ChannelBuilder): KSelector = {
    channelBuilder match {
      case reconfigurable: Reconfigurable => config.addReconfigurable(reconfigurable)
      case _ =>
    }
    new KSelector(
      maxRequestSize,
      connectionsMaxIdleMs,
      failedAuthenticationDelayMs,
      metrics,
      time,
      "socket-server",
      metricTags,
      false,
      true,
      channelBuilder,
      memoryPool,
      logContext)
  }

  // Connection ids have the format `localAddr:localPort-remoteAddr:remotePort-index`. The index is a
  // non-negative incrementing value that ensures that even if remotePort is reused after a connection is
  // closed, connection ids are not reused while requests from the closed connection are being processed.
  private var nextConnectionIndex = 0

  override def run() {
    // todo: 等待Processor线程启动完成
    startupComplete()
    try {
      while (isRunning) {
        try {
          //todo:  读取每一个SocketChannel，然后向Selector上注册OP_READ事件
          // setup any new connections that have been queued up
          configureNewConnections()

          // register any new responses for writing
          //todo:  注册一个response对象, 发送Response，并将Response放入到inflightResponses临时队列
          processNewResponses()

          //todo:  执行NIO poll，获取对应SocketChannel上准备就绪的I/O操作
          poll()

          //todo: 将接收到的Request放入Request队列
          processCompletedReceives()

          //todo: 用来处理已经完成的请求,为临时Response队列中的Response执行回调逻辑
          processCompletedSends()

          // 处理因发送失败而导致的连接断开
          processDisconnected()
        } catch {
          // We catch all the throwables here to prevent the processor thread from exiting. We do this because
          // letting a processor exit might cause a bigger impact on the broker. This behavior might need to be
          // reviewed if we see an exception that needs the entire broker to stop. Usually the exceptions thrown would
          // be either associated with a specific socket channel or a bad request. These exceptions are caught and
          // processed by the individual methods above which close the failing channel and continue processing other
          // channels. So this catch block should only ever see ControlThrowables.
          case e: Throwable => processException("Processor got uncaught exception.", e)
        }
      }
    } finally {
      debug("Closing selector - processor " + id)
      CoreUtils.swallow(closeAll(), this, Level.ERROR)
      shutdownComplete()
    }
  }

  private def processException(errorMessage: String, throwable: Throwable) {
    throwable match {
      case e: ControlThrowable => throw e
      case e => error(errorMessage, e)
    }
  }

  private def processChannelException(channelId: String, errorMessage: String, throwable: Throwable) {
    if (openOrClosingChannel(channelId).isDefined) {
      error(s"Closing socket for $channelId because of error", throwable)
      close(channelId)
    }
    processException(errorMessage, throwable)
  }

  private def processNewResponses() {
    var currentResponse: RequestChannel.Response = null
     //todo: currentResponse不为空
    while ({currentResponse = dequeueResponse(); currentResponse != null}) {
      //获取对应的channelId
      val channelId = currentResponse.request.context.connectionId
      try {
        currentResponse match {
          //acks=0
          //不需要返回响应给客户端，内部再次绑定OP_READ事件，接受客户端请求
          case response: NoOpResponse =>
            // There is no response to send to the client, we need to read more pipelined requests
            // that are sitting in the server's socket buffer
            updateRequestMetrics(response)
            trace("Socket server received empty response to send, registering for read: " + response)
            // Try unmuting the channel. If there was no quota violation and the channel has not been throttled,
            // it will be unmuted immediately. If the channel has been throttled, it will be unmuted only if the
            // throttling delay has already passed by now.
            handleChannelMuteEvent(channelId, ChannelMuteEvent.RESPONSE_SENT)
            tryUnmuteChannel(channelId)

          //todo: 服务端给客户端返回响应的分支
          case response: SendResponse =>
            sendResponse(response, response.responseSend)
          case response: CloseConnectionResponse =>
            updateRequestMetrics(response)
            trace("Closing socket connection actively according to the response code.")
            close(channelId)
          case _: StartThrottlingResponse =>
            handleChannelMuteEvent(channelId, ChannelMuteEvent.THROTTLE_STARTED)
          case _: EndThrottlingResponse =>
            // Try unmuting the channel. The channel will be unmuted only if the response has already been sent out to
            // the client.
            handleChannelMuteEvent(channelId, ChannelMuteEvent.THROTTLE_ENDED)
            tryUnmuteChannel(channelId)
          case _ =>
            throw new IllegalArgumentException(s"Unknown response type: ${currentResponse.getClass}")
        }
      } catch {
        case e: Throwable =>
          processChannelException(channelId, s"Exception while processing response for $channelId", e)
      }
    }
  }

  /* `protected` for test usage */
  protected[network] def sendResponse(response: RequestChannel.Response, responseSend: Send) {
    //todo: 获取连接id
    val connectionId = response.request.context.connectionId
    trace(s"Socket server received response to send to $connectionId, registering for write and sending data: $response")
    // `channel` can be None if the connection was closed remotely or if selector closed it for being idle for too long
    ///todo:kafkaChannel为空异常情况
    if (channel(connectionId).isEmpty) {
      warn(s"Attempting to send response via channel for which there is no open connection, connection id $connectionId")
      response.request.updateRequestMetrics(0L, response)
    }
    // Invoke send for closingChannel as well so that the send is failed and the channel closed properly and
    // removed from the Selector after discarding any pending staged receives.
    // `openOrClosingChannel` can be None if the selector closed the connection because it was idle for too long
    //todo: 正常情况, 如果该连接处于可连接状态
    if (openOrClosingChannel(connectionId).isDefined) {
      //向客户端生产者返回响应
      selector.send(responseSend)
      //缓存响应到map中
      inflightResponses += (connectionId -> response)
    }
  }

  private def poll() {
    try selector.poll(300)
    catch {
      case e @ (_: IllegalStateException | _: IOException) =>
        // The exception is not re-thrown and any completed sends/receives/connections/disconnections
        // from this poll will be processed.
        error(s"Processor $id poll failed", e)
    }
  }

  private def processCompletedReceives() {
    //todo: 遍历每一个请求
    selector.completedReceives.asScala.foreach { receive =>
      try {
        //todo: 获取连接对应的kafkaChannel
        openOrClosingChannel(receive.source) match {
            //模式匹配
          case Some(channel) =>
            //获取header信息
            val header = RequestHeader.parse(receive.payload)
            //获取连接id
            val connectionId = receive.source
            val context = new RequestContext(header, connectionId, channel.socketAddress,
              channel.principal, listenerName, securityProtocol)
           // todo: 对于获取到的请求按照协议进行解析，解析成一个request对象
            val req = new RequestChannel.Request(processor = id, context = context,
              startTimeNanos = time.nanoseconds, memoryPool, receive.payload, requestChannel.metrics)
            //todo: 把request请求放入队列
            requestChannel.sendRequest(req)
            //todo: 对当前的连接移除掉OP_READ事件
            selector.mute(connectionId)
            handleChannelMuteEvent(connectionId, ChannelMuteEvent.REQUEST_RECEIVED)
          case None =>
            // This should never happen since completed receives are processed immediately after `poll()`
            throw new IllegalStateException(s"Channel ${receive.source} removed from selector before processing completed receive")
        }
      } catch {
        // note that even though we got an exception, we can assume that receive.source is valid.
        // Issues with constructing a valid receive object were handled earlier
        case e: Throwable =>
          processChannelException(receive.source, s"Exception while processing request from ${receive.source}", e)
      }
    }
  }

  private def processCompletedSends() {
    //todo: 遍历底层SocketChannel已发送的Response
    selector.completedSends.asScala.foreach { send =>
      try {
        //移除掉对应的缓存中的response
        val response = inflightResponses.remove(send.destination).getOrElse {
          throw new IllegalStateException(s"Send for ${send.destination} completed, but not in `inflightResponses`")
        }
        // 更新一些统计指标
        updateRequestMetrics(response)

        // 执行回调逻辑
        // Invoke send completion callback
        response.onComplete.foreach(onComplete => onComplete(send))

        // Try unmuting the channel. If there was no quota violation and the channel has not been throttled,
        // it will be unmuted immediately. If the channel has been throttled, it will unmuted only if the throttling
        // delay has already passed by now.
        handleChannelMuteEvent(send.destination, ChannelMuteEvent.RESPONSE_SENT)
        //todo: 重新注册OP_READ,又可以监听客户端的请求
        tryUnmuteChannel(send.destination)
      } catch {
        case e: Throwable => processChannelException(send.destination,
          s"Exception while processing completed send to ${send.destination}", e)
      }
    }
  }

  private def updateRequestMetrics(response: RequestChannel.Response): Unit = {
    val request = response.request
    val networkThreadTimeNanos = openOrClosingChannel(request.context.connectionId).fold(0L)(_.getAndResetNetworkThreadTimeNanos())
    request.updateRequestMetrics(networkThreadTimeNanos, response)
  }

  private def processDisconnected() {
    selector.disconnected.keySet.asScala.foreach { connectionId =>
      try {
        val remoteHost = ConnectionId.fromString(connectionId).getOrElse {
          throw new IllegalStateException(s"connectionId has unexpected format: $connectionId")
        }.remoteHost
        inflightResponses.remove(connectionId).foreach(updateRequestMetrics)
        // the channel has been closed by the selector but the quotas still need to be updated
        connectionQuotas.dec(InetAddress.getByName(remoteHost))
      } catch {
        case e: Throwable => processException(s"Exception while processing disconnection of $connectionId", e)
      }
    }
  }

  /**
   * Close the connection identified by `connectionId` and decrement the connection count.
   * The channel will be immediately removed from the selector's `channels` or `closingChannels`
   * and no further disconnect notifications will be sent for this channel by the selector.
   * If responses are pending for the channel, they are dropped and metrics is updated.
   * If the channel has already been removed from selector, no action is taken.
   */
  private def close(connectionId: String): Unit = {
    openOrClosingChannel(connectionId).foreach { channel =>
      debug(s"Closing selector connection $connectionId")
      val address = channel.socketAddress
      if (address != null)
        connectionQuotas.dec(address)
      selector.close(connectionId)

      inflightResponses.remove(connectionId).foreach(response => updateRequestMetrics(response))
    }
  }

  /**
   * Queue up a new connection for reading
   */
  def accept(socketChannel: SocketChannel) {
    //todo: 把socketChannel存储到自己的队列中
    newConnections.add(socketChannel)
    wakeup()
  }

  /**
   * Register any new connections that have been queued up
   */
  private def configureNewConnections() {
    //todo: 如果连接队列不为空
    while (!newConnections.isEmpty) {
      //todo: 不断获取连接队列中的socketChannel
      val channel = newConnections.poll()
      try {
        debug(s"Processor $id listening to new connection from ${channel.socket.getRemoteSocketAddress}")
        //todo：向Processor上的Selector注册对应的事件
        selector.register(connectionId(channel.socket), channel)
      } catch {
        // We explicitly catch all exceptions and close the socket to avoid a socket leak.
        case e: Throwable =>
          val remoteAddress = channel.socket.getRemoteSocketAddress
          // need to close the channel here to avoid a socket leak.
          close(channel)
          processException(s"Processor $id closed connection from $remoteAddress", e)
      }
    }
  }

  /**
   * Close the selector and all open connections
   */
  private def closeAll() {
    selector.channels.asScala.foreach { channel =>
      close(channel.id)
    }
    selector.close()
    removeMetric(IdlePercentMetricName, Map(NetworkProcessorMetricTag -> id.toString))
  }

  // 'protected` to allow override for testing
  protected[network] def connectionId(socket: Socket): String = {
    //todo: 获取socketChannel中的各种参数
    val localHost = socket.getLocalAddress.getHostAddress
    val localPort = socket.getLocalPort
    val remoteHost = socket.getInetAddress.getHostAddress
    val remotePort = socket.getPort
    val connId = ConnectionId(localHost, localPort, remoteHost, remotePort, nextConnectionIndex).toString
    nextConnectionIndex = if (nextConnectionIndex == Int.MaxValue) 0 else nextConnectionIndex + 1
    connId
  }

  private[network] def enqueueResponse(response: RequestChannel.Response): Unit = {
    //todo: 添加response到响应队列中
    responseQueue.put(response)
    wakeup()
  }

  private def dequeueResponse(): RequestChannel.Response = {
    //从响应队列中取出一个响应
    val response = responseQueue.poll()
    if (response != null)
      response.request.responseDequeueTimeNanos = Time.SYSTEM.nanoseconds
    response
  }

  private[network] def responseQueueSize = responseQueue.size

  // Only for testing
  private[network] def inflightResponseCount: Int = inflightResponses.size

  // Visible for testing
  // Only methods that are safe to call on a disconnected channel should be invoked on 'openOrClosingChannel'.
  private[network] def openOrClosingChannel(connectionId: String): Option[KafkaChannel] =
    Option(selector.channel(connectionId)).orElse(Option(selector.closingChannel(connectionId)))

  // Indicate the specified channel that the specified channel mute-related event has happened so that it can change its
  // mute state.
  private def handleChannelMuteEvent(connectionId: String, event: ChannelMuteEvent): Unit = {
    openOrClosingChannel(connectionId).foreach(c => c.handleChannelMuteEvent(event))
  }

  private def tryUnmuteChannel(connectionId: String) = {
    openOrClosingChannel(connectionId).foreach(c => selector.unmute(c.id))
  }

  /* For test usage */
  private[network] def channel(connectionId: String): Option[KafkaChannel] =
    Option(selector.channel(connectionId))

  // Visible for testing
  private[network] def numStagedReceives(connectionId: String): Int =
    openOrClosingChannel(connectionId).map(c => selector.numStagedReceives(c)).getOrElse(0)

  /**
   * Wakeup the thread for selection.
   */
  override def wakeup() = selector.wakeup()

  override def shutdown(): Unit = {
    super.shutdown()
    removeMetric("IdlePercent", Map("networkProcessor" -> id.toString))
  }

}

class ConnectionQuotas(val defaultMax: Int, overrideQuotas: Map[String, Int]) {

  @volatile private var defaultMaxConnectionsPerIp = defaultMax
  @volatile private var maxConnectionsPerIpOverrides = overrideQuotas.map { case (host, count) => (InetAddress.getByName(host), count) }
  private val counts = mutable.Map[InetAddress, Int]()

  def inc(address: InetAddress) {
    counts.synchronized {
      val count = counts.getOrElseUpdate(address, 0)
      counts.put(address, count + 1)
      val max = maxConnectionsPerIpOverrides.getOrElse(address, defaultMaxConnectionsPerIp)
      if (count >= max)
        throw new TooManyConnectionsException(address, max)
    }
  }

  def updateMaxConnectionsPerIp(maxConnectionsPerIp: Int): Unit = {
    defaultMaxConnectionsPerIp = maxConnectionsPerIp
  }

  def updateMaxConnectionsPerIpOverride(overrideQuotas: Map[String, Int]): Unit = {
    maxConnectionsPerIpOverrides = overrideQuotas.map { case (host, count) => (InetAddress.getByName(host), count) }
  }

  def dec(address: InetAddress) {
    counts.synchronized {
      val count = counts.getOrElse(address,
        throw new IllegalArgumentException(s"Attempted to decrease connection count for address with no connections, address: $address"))
      if (count == 1)
        counts.remove(address)
      else
        counts.put(address, count - 1)
    }
  }

  def get(address: InetAddress): Int = counts.synchronized {
    counts.getOrElse(address, 0)
  }

}

class TooManyConnectionsException(val ip: InetAddress, val count: Int) extends KafkaException("Too many connections from %s (maximum = %d)".format(ip, count))
