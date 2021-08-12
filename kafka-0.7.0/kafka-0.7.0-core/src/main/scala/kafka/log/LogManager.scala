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

package kafka.log

import java.io._
import org.apache.log4j.Logger
import kafka.utils._
import scala.actors.Actor
import scala.collection._
import java.util.concurrent.CountDownLatch
import kafka.server.{KafkaConfig, KafkaZooKeeper}
import kafka.common.{InvalidTopicException, InvalidPartitionException}

/**
 * The guy who creates and hands out logs
 */
@threadsafe
private[kafka] class LogManager(val config: KafkaConfig,
                                private val scheduler: KafkaScheduler,
                                private val time: Time,
                                val logCleanupIntervalMs: Long,
                                val logCleanupDefaultAgeMs: Long,
                                needRecovery: Boolean) {
  
  val logDir: File = new File(config.logDir)
  private val numPartitions = config.numPartitions
  private val maxSize: Long = config.logFileSize  // 日志分片的最大值，如果超过，就可能回滚
  private val flushInterval = config.flushInterval
  private val topicPartitionsMap = config.topicPartitionsMap
  private val logger = Logger.getLogger(classOf[LogManager])
  private val logCreationLock = new Object
  private val random = new java.util.Random
  private var kafkaZookeeper: KafkaZooKeeper = null
  private var zkActor: Actor = null
  private val startupLatch: CountDownLatch = if (config.enableZookeeper) new CountDownLatch(1) else null
  private val logFlusherScheduler = new KafkaScheduler(1, "kafka-logflusher-", false)
  private val logFlushIntervalMap = config.flushIntervalMap
  private val logRetentionMSMap = getLogRetentionMSMap(config.logRetentionHoursMap)
  private val logRetentionSize = config.logRetentionSize
  println("--------------------LogManager-------init-------------logDir---------:"+ logDir)
  /* Initialize a log for each subdirectory of the main log directory */
  private val logs = new Pool[String, Pool[Int, Log]]()
  if(!logDir.exists()) { // 判断日志目录是否存在
    logger.info("No log directory found, creating '" + logDir.getAbsolutePath() + "'")
    logDir.mkdirs() // 不存在就创建
  }
  if(!logDir.isDirectory() || !logDir.canRead()){ // 如果不是目录，或者不可读
    throw new IllegalArgumentException(logDir.getAbsolutePath() + " is not a readable log directory.")
  }
  val subDirs = logDir.listFiles() // 获取所有的子目录列表文件
  if(subDirs != null) {
    for(dir <- subDirs) { // 开始遍历
      if(!dir.isDirectory()) {
        logger.warn("Skipping unexplainable file '" + dir.getAbsolutePath() + "'--should it be there?")
      } else { // 如果是文件目录
        logger.info("Loading log '" + dir.getName() + "'")
        println("--------------------LogManager-------init-------------dir---------:"+ dir)
        val log = new Log(dir, maxSize, flushInterval, needRecovery) // 创建Log对象
        val topicPartion = Utils.getTopicPartition(dir.getName) // 获取topic 和 partition
        logs.putIfNotExists(topicPartion._1, new Pool[Int, Log]())   // 放入缓冲中
        val parts = logs.get(topicPartion._1)
        parts.put(topicPartion._2, log) // 分区和文件对象建立对应关系
      }
    }
  }
  
  /* Schedule the cleanup task to delete old logs */
  if(scheduler != null) {
    logger.info("starting log cleaner every " + logCleanupIntervalMs + " ms")    
    scheduler.scheduleWithRate(cleanupLogs, 60 * 1000, logCleanupIntervalMs)
  }

  if(config.enableZookeeper) {
    kafkaZookeeper = new KafkaZooKeeper(config, this)
    kafkaZookeeper.startup
    zkActor = new Actor {
      def act() {
        loop {
          receive {
            case topic: String =>
              try {
                kafkaZookeeper.registerTopicInZk(topic)
              }
              catch {
                case e => logger.error(e) // log it and let it go
              }
            case StopActor =>
              logger.info("zkActor stopped")
              exit
          }
        }
      }
    }
    zkActor.start
  }

  case object StopActor

  private def getLogRetentionMSMap(logRetentionHourMap: Map[String, Int]) : Map[String, Long] = {
    var ret = new mutable.HashMap[String, Long]
    for ( (topic, hour) <- logRetentionHourMap )
      ret.put(topic, hour * 60 * 60 * 1000L)
    ret
  }

  /**
   *  Register this broker in ZK for the first time.
   */
  def startup() {
    if(config.enableZookeeper) {// 是否使用外部的zk
      logger.info("----------LogManager--------startup------------使用外部的zk服务-------------------")
      kafkaZookeeper.registerBrokerInZk()
      for (topic <- getAllTopics)
        kafkaZookeeper.registerTopicInZk(topic)
      startupLatch.countDown
    }
    logger.info("Starting log flusher every " + config.flushSchedulerThreadRate + " ms with the following overrides " + logFlushIntervalMap)
    logFlusherScheduler.scheduleWithRate(flushAllLogs, config.flushSchedulerThreadRate, config.flushSchedulerThreadRate)
  }

  private def awaitStartup() {
    if (config.enableZookeeper)
      startupLatch.await
  }

  def registerNewTopicInZK(topic: String) {
    if (config.enableZookeeper)
      zkActor ! topic 
  }

  /**
   * Create a log for the given topic and the given partition
   */
  private def createLog(topic: String, partition: Int): Log = {
    println("-------------LogManager--------------createLog-------------根据topic和分区--创建日志目录-----------")
    logCreationLock synchronized {
      val d = new File(logDir, topic + "-" + partition)  //  创建日志目录
      d.mkdirs()
      new Log(d, maxSize, flushInterval, false)
    }
  }
  

  def chooseRandomPartition(topic: String): Int = {
    random.nextInt(topicPartitionsMap.getOrElse(topic, numPartitions))
  }

  /**
   * Create the log if it does not exist, if it exists just return it
   */
  def getOrCreateLog(topic: String, partition: Int): Log = { // 需要根据topic和 partition分区，创建Log对象
    println("-------------LogManager----getOrCreateLog--------------------------")
    awaitStartup
    if (topic.length <= 0){
      throw new InvalidTopicException("topic name can't be empty")
    }
    if (partition < 0 || partition >= topicPartitionsMap.getOrElse(topic, numPartitions)) {
      logger.warn("Wrong partition " + partition + " valid partitions (0," + (topicPartitionsMap.getOrElse(topic, numPartitions) - 1) + ")")
      throw new InvalidPartitionException("wrong partition " + partition)
    }
    var hasNewTopic = false
    var parts: Pool[Int, Log] = logs.get(topic) // 根据topic从缓存池中，获取是否存在对应的分区的日志分片
    if (parts == null) { // 如果不存在，就new一个对象，存储到池子中
      val found = logs.putIfNotExists(topic, new Pool[Int, Log])
      if (found == null) {
        hasNewTopic = true // 说明是新的topic
      }
      parts = logs.get(topic) // 再次获取
    }
    var log = parts.get(partition) // 然后根据分区获取，log对象实例
    if(log == null) {  // 如果没有日志文件目录，就创建一个
      log = createLog(topic, partition)
      val found = parts.putIfNotExists(partition, log)
      if(found != null) {
        // there was already somebody there
        log.close()
        log = found
      } else{
        logger.info("Created log for '" + topic + "'-" + partition)
      }
    }

    if (hasNewTopic){ // 如果是新的topic，就注册到zk
      registerNewTopicInZK(topic)
    }
    log
  }
  
  /* Attemps to delete all provided segments from a log and returns how many it was able to */
  private def deleteSegments(log: Log, segments: Seq[LogSegment]): Int = {
    var total = 0
    for(segment <- segments) {
      logger.info("Deleting log segment " + segment.file.getName() + " from " + log.name)
      Utils.swallow(logger.warn, segment.messageSet.close())
      if(!segment.file.delete()) {
        logger.warn("Delete failed.")
      } else {
        total += 1
      }
    }
    total
  }

  /* Runs through the log removing segments older than a certain age */
  private def cleanupExpiredSegments(log: Log): Int = {
    println(Thread.currentThread().getName + "-------------LogManager-------------------cleanupExpiredSegments-----------start-------")
    val startMs = time.milliseconds
    val topic = Utils.getTopicPartition(log.dir.getName)._1
    val logCleanupThresholdMS = logRetentionMSMap.get(topic).getOrElse(this.logCleanupDefaultAgeMs)
    // 删除过期的文件，如果文件最后修改时间和当前时间做时间差，大于设置时间，就满足条件，删除
    val toBeDeleted = log.markDeletedWhile(startMs - _.file.lastModified > logCleanupThresholdMS)
    val total = deleteSegments(log, toBeDeleted) // 删除特定的日志分片
    println(Thread.currentThread().getName + "-------------LogManager-------------------cleanupExpiredSegments-----------end-------")
    total
  }

  /**
   *  Runs through the log removing segments until the size of the log
   *  is at least logRetentionSize bytes in size
   */
  private def cleanupSegmentsToMaintainSize(log: Log): Int = {
    if(logRetentionSize < 0 || log.size < logRetentionSize) return 0
    var diff = log.size - logRetentionSize
    def shouldDelete(segment: LogSegment) = {
      if(diff - segment.size >= 0) {
        diff -= segment.size
        true
      } else {
        false
      }
    }
    val toBeDeleted = log.markDeletedWhile( shouldDelete )
    val total = deleteSegments(log, toBeDeleted)
    total
  }

  /**
   * Delete any eligible logs. Return the number of segments deleted.
   */
  def cleanupLogs() {
    println(Thread.currentThread().getName + "-------------LogManager--------定时清理日志线程-----------cleanupLogs-----------start-------")
    logger.debug("Beginning log cleanup...")
    val iter = getLogIterator
    var total = 0
    val startMs = time.milliseconds
    while(iter.hasNext) {
      val log = iter.next
      logger.debug("Garbage collecting '" + log.name + "'")
      total += cleanupExpiredSegments(log) + cleanupSegmentsToMaintainSize(log)
    }
    logger.debug("Log cleanup completed. " + total + " files deleted in " + (time.milliseconds - startMs) / 1000 + " seconds")
    println(Thread.currentThread().getName +"-------------LogManager----------定时清理日志线程---------cleanupLogs-----------end-------")
  }
  
  /**
   * Close all the logs
   */
  def close() {
    logFlusherScheduler.shutdown()
    val iter = getLogIterator
    while(iter.hasNext)
      iter.next.close()
    if (config.enableZookeeper) {
      zkActor ! StopActor
      kafkaZookeeper.close
    }
  }
  
  private def getLogIterator(): Iterator[Log] = {
    new IteratorTemplate[Log] {
      val partsIter = logs.values.iterator
      var logIter: Iterator[Log] = null

      override def makeNext(): Log = {
        while (true) {
          if (logIter != null && logIter.hasNext)
            return logIter.next
          if (!partsIter.hasNext)
            return allDone
          logIter = partsIter.next.values.iterator
        }
        // should never reach here
        assert(false)
        return allDone
      }
    }
  }

  private def flushAllLogs() = {
    //println(Thread.currentThread().getName +"-------------LogManager---------flushAllLogs-----刷新日志-----------start-------")
    if (logger.isDebugEnabled)
      logger.debug("flushing the high watermark of all logs")

    for (log <- getLogIterator) {
      try{
        val timeSinceLastFlush = System.currentTimeMillis - log.getLastFlushedTime
        var logFlushInterval = config.defaultFlushIntervalMs
        if(logFlushIntervalMap.contains(log.getTopicName)) {
          logFlushInterval = logFlushIntervalMap(log.getTopicName)
        }
        if (logger.isDebugEnabled){
          logger.debug(log.getTopicName + " flush interval  " + logFlushInterval + " last flushed " + log.getLastFlushedTime + " timesincelastFlush: " + timeSinceLastFlush)
        }
        if(timeSinceLastFlush >= logFlushInterval) {
          log.flush
        }
      } catch {
        case e =>
          logger.error("Error flushing topic " + log.getTopicName, e)
          e match {
            case _: IOException =>
              logger.fatal("Halting due to unrecoverable I/O error while flushing logs: " + e.getMessage, e)
              Runtime.getRuntime.halt(1)
            case _ =>
          }
      }
    }
   // println(Thread.currentThread().getName +"-------------LogManager---------flushAllLogs-----刷新日志-----------end-------")
  }


  def getAllTopics(): Iterator[String] = logs.keys.iterator
  def getTopicPartitionsMap() = topicPartitionsMap
}
