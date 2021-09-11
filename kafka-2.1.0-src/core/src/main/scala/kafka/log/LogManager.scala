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
import java.nio.file.Files
import java.util.concurrent._

import com.yammer.metrics.core.Gauge
import kafka.metrics.KafkaMetricsGroup
import kafka.server.checkpoints.OffsetCheckpointFile
import kafka.server.{BrokerState, RecoveringFromUncleanShutdown, _}
import kafka.utils._
import kafka.zk.KafkaZkClient
import org.apache.kafka.common.{KafkaException, TopicPartition}
import org.apache.kafka.common.utils.Time
import org.apache.kafka.common.errors.{KafkaStorageException, LogDirNotFoundException}

import scala.collection.JavaConverters._
import scala.collection._
import scala.collection.mutable.ArrayBuffer

/**
 * The entry point to the kafka log management subsystem. The log manager is responsible for log creation, retrieval, and cleaning.
 * All read and write operations are delegated to the individual log instances.
 *
 * The log manager maintains logs in one or more directories. New logs are created in the data directory
 * with the fewest logs. No attempt is made to move partitions after the fact or balance based on
 * size or I/O rate.
 *
 * A background thread handles log retention by periodically truncating excess log segments.
 */
@threadsafe
class LogManager(logDirs: Seq[File],
                 initialOfflineDirs: Seq[File],
                 val topicConfigs: Map[String, LogConfig], // note that this doesn't get updated after creation
                 val initialDefaultConfig: LogConfig,
                 val cleanerConfig: CleanerConfig,
                 recoveryThreadsPerDataDir: Int,
                 val flushCheckMs: Long,
                 val flushRecoveryOffsetCheckpointMs: Long,
                 val flushStartOffsetCheckpointMs: Long,
                 val retentionCheckMs: Long,
                 val maxPidExpirationMs: Int,
                 scheduler: Scheduler,
                 val brokerState: BrokerState,
                 brokerTopicStats: BrokerTopicStats,
                 logDirFailureChannel: LogDirFailureChannel,
                 time: Time) extends Logging with KafkaMetricsGroup {

  import LogManager._
  //默认的锁文件.kafka不正常关闭的时候可以看见这个文件未被清理.在logdir下.
  val LockFile = ".lock"
  //初始任务延迟时长
  val InitialTaskDelayMs = 30 * 1000
  //创建或删除 Log 时的锁对象
  private val logCreationOrDeletionLock = new Object
  /** 记录每个 topic 分区对象与 Log 对象之间的映射关系 */
  private val currentLogs = new Pool[TopicPartition, Log]()
  // Future logs are put in the directory with "-future" suffix. Future log is created when user wants to move replica
  // from one log directory to another log directory on the same broker. The directory of the future log will be renamed
  // to replace the current log of the partition after the future log catches up with the current log
  private val futureLogs = new Pool[TopicPartition, Log]()
  // Each element in the queue contains the log object to be deleted and the time it is scheduled for deletion.
  /** 记录需要被删除的 Log 对象 */
  private val logsToBeDeleted = new LinkedBlockingQueue[(Log, Long)]()

  //todo: 检查目录是否合法,并且创建目录
  private val _liveLogDirs: ConcurrentLinkedQueue[File] = createAndValidateLogDirs(logDirs, initialOfflineDirs)
  @volatile private var _currentDefaultConfig = initialDefaultConfig
  @volatile private var numRecoveryThreadsPerDataDir = recoveryThreadsPerDataDir

  def reconfigureDefaultLogConfig(logConfig: LogConfig): Unit = {
    this._currentDefaultConfig = logConfig
  }

  def currentDefaultConfig: LogConfig = _currentDefaultConfig

  def liveLogDirs: Seq[File] = {
    if (_liveLogDirs.size == logDirs.size)
      logDirs
    else
      _liveLogDirs.asScala.toBuffer
  }

  /** 尝试对每个 log 目录在文件系统层面加锁，这里加的是进程锁 */
  private val dirLocks = lockLogDirs(liveLogDirs)
  /**
    * 遍历为每个 log 目录创建一个操作其名下 recovery-point-offset-checkpoint 文件的 OffsetCheckpoint 对象，
    * 并建立映射关系
    */
  @volatile private var recoveryPointCheckpoints = liveLogDirs.map(dir =>
    (dir, new OffsetCheckpointFile(new File(dir, RecoveryPointCheckpointFile), logDirFailureChannel))).toMap
  @volatile private var logStartOffsetCheckpoints = liveLogDirs.map(dir =>
    (dir, new OffsetCheckpointFile(new File(dir, LogStartOffsetCheckpointFile), logDirFailureChannel))).toMap

  private val preferredLogDirs = new ConcurrentHashMap[TopicPartition, String]()

  private def offlineLogDirs: Iterable[File] = {
    val logDirsSet = mutable.Set[File](logDirs: _*)
    _liveLogDirs.asScala.foreach(logDirsSet -=)
    logDirsSet
  }

  //加载该节点所有日志目录
  loadLogs()

  // public, so we can access this from kafka.admin.DeleteTopicTest
  val cleaner: LogCleaner =
    if(cleanerConfig.enableCleaner)
      new LogCleaner(cleanerConfig, liveLogDirs, currentLogs, logDirFailureChannel, time = time)
    else
      null

  val offlineLogDirectoryCount = newGauge(
    "OfflineLogDirectoryCount",
    new Gauge[Int] {
      def value = offlineLogDirs.size
    }
  )

  for (dir <- logDirs) {
    newGauge(
      "LogDirectoryOffline",
      new Gauge[Int] {
        def value = if (_liveLogDirs.contains(dir)) 0 else 1
      },
      Map("logDirectory" -> dir.getAbsolutePath)
    )
  }

  /**
   * Create and check validity of the given directories that are not in the given offline directories, specifically:
   * <ol>
   * <li> Ensure that there are no duplicates in the directory list
   * <li> Create each directory if it doesn't exist
   * <li> Check that each path is a readable directory
   * </ol>
   */
  private def createAndValidateLogDirs(dirs: Seq[File], initialOfflineDirs: Seq[File]): ConcurrentLinkedQueue[File] = {
    val liveLogDirs = new ConcurrentLinkedQueue[File]()
    val canonicalPaths = mutable.HashSet.empty[String]

    //todo: 遍历目录   log.dirs=/data0/log, /data1/log, /data2/log
    //log.dirs=/kkb/install/kafka/kafka-logs1,/kkb/install/kafka/kafka-logs2,/kkb/install/kafka/kafka-logs3
    for (dir <- dirs) {
      try {
        //liveLogDirs与offline日志目录不能有重叠情况
        if (initialOfflineDirs.contains(dir))
          throw new IOException(s"Failed to load ${dir.getAbsolutePath} during broker startup")

        // 如果日志目录不存在，则创建出来
        if (!dir.exists) {
          info(s"Log directory ${dir.getAbsolutePath} not found, creating it.")
          //todo: 代码第一次进来会创建所有的目录
          val created = dir.mkdirs()
          if (!created)
            throw new IOException(s"Failed to create data directory ${dir.getAbsolutePath}")
        }
        // todo: 确保每个日志目录是个目录且有可读权限
        if (!dir.isDirectory || !dir.canRead)
          throw new IOException(s"${dir.getAbsolutePath} is not a readable log directory.")

        // getCanonicalPath() throws IOException if a file system query fails or if the path is invalid (e.g. contains
        // the Nul character). Since there's no easy way to distinguish between the two cases, we treat them the same
        // and mark the log directory as offline.
        if (!canonicalPaths.add(dir.getCanonicalPath))
          throw new KafkaException(s"Duplicate log directory found: ${dirs.mkString(", ")}")

         //添加目录到ConcurrentLinkedQueue[File]队列中
        liveLogDirs.add(dir)
      } catch {
        case e: IOException =>
          logDirFailureChannel.maybeAddOfflineLogDir(dir.getAbsolutePath, s"Failed to create or validate data directory ${dir.getAbsolutePath}", e)
      }
    }
    //工作log目录不能为空，一个都没有则直接退出
    if (liveLogDirs.isEmpty) {
      fatal(s"Shutdown broker because none of the specified log dirs from ${dirs.mkString(", ")} can be created or validated")
      Exit.halt(1)
    }

    //返回
    liveLogDirs
  }

  def resizeRecoveryThreadPool(newSize: Int): Unit = {
    info(s"Resizing recovery thread pool size for each data dir from $numRecoveryThreadsPerDataDir to $newSize")
    numRecoveryThreadsPerDataDir = newSize
  }

  // dir should be an absolute path
  def handleLogDirFailure(dir: String) {
    info(s"Stopping serving logs in dir $dir")
    logCreationOrDeletionLock synchronized {
      _liveLogDirs.remove(new File(dir))
      if (_liveLogDirs.isEmpty) {
        fatal(s"Shutdown broker because all log dirs in ${logDirs.mkString(", ")} have failed")
        Exit.halt(1)
      }

      recoveryPointCheckpoints = recoveryPointCheckpoints.filter { case (file, _) => file.getAbsolutePath != dir }
      logStartOffsetCheckpoints = logStartOffsetCheckpoints.filter { case (file, _) => file.getAbsolutePath != dir }
      if (cleaner != null)
        cleaner.handleLogDirFailure(dir)

      val offlineCurrentTopicPartitions = currentLogs.collect {
        case (tp, log) if log.dir.getParent == dir => tp
      }
      offlineCurrentTopicPartitions.foreach { topicPartition => {
        val removedLog = currentLogs.remove(topicPartition)
        if (removedLog != null) {
          removedLog.closeHandlers()
          removedLog.removeLogMetrics()
        }
      }}

      val offlineFutureTopicPartitions = futureLogs.collect {
        case (tp, log) if log.dir.getParent == dir => tp
      }
      offlineFutureTopicPartitions.foreach { topicPartition => {
        val removedLog = futureLogs.remove(topicPartition)
        if (removedLog != null) {
          removedLog.closeHandlers()
          removedLog.removeLogMetrics()
        }
      }}

      info(s"Logs for partitions ${offlineCurrentTopicPartitions.mkString(",")} are offline and " +
           s"logs for future partitions ${offlineFutureTopicPartitions.mkString(",")} are offline due to failure on log directory $dir")
      dirLocks.filter(_.file.getParent == dir).foreach(dir => CoreUtils.swallow(dir.destroy(), this))
    }
  }

  /**
   * Lock all the given directories
   */
  private def lockLogDirs(dirs: Seq[File]): Seq[FileLock] = {
    dirs.flatMap { dir =>
      try {
        val lock = new FileLock(new File(dir, LockFile))
        if (!lock.tryLock())
          throw new KafkaException("Failed to acquire lock on file .lock in " + lock.file.getParent +
            ". A Kafka instance in another process or thread is using this directory.")
        Some(lock)
      } catch {
        case e: IOException =>
          logDirFailureChannel.maybeAddOfflineLogDir(dir.getAbsolutePath, s"Disk error while locking directory $dir", e)
          None
      }
    }
  }

  private def addLogToBeDeleted(log: Log): Unit = {
    this.logsToBeDeleted.add((log, time.milliseconds()))
  }

  // Only for testing
  private[log] def hasLogsToBeDeleted: Boolean = !logsToBeDeleted.isEmpty

  //todo: loadLog()方法主要是根据一些参数来对每个TopicPartition生成Log对象，用于对每个TopicPartition的日志的操作
  private def loadLog(logDir: File, recoveryPoints: Map[TopicPartition, Long], logStartOffsets: Map[TopicPartition, Long]): Unit = {
    debug("Loading log '" + logDir.getName + "'")
    //根据目录名解析partiton的信息  比如test-0，解析等到的patition就是topic test下的0号分区
    val topicPartition = Log.parseTopicPartitionName(logDir)
    val config = topicConfigs.getOrElse(topicPartition.topic, currentDefaultConfig)
    val logRecoveryPoint = recoveryPoints.getOrElse(topicPartition, 0L)
    val logStartOffset = logStartOffsets.getOrElse(topicPartition, 0L)

    //todo: 构建Log对象
    val log = Log(
      dir = logDir,
      config = config,
      logStartOffset = logStartOffset,
      recoveryPoint = logRecoveryPoint,
      maxProducerIdExpirationMs = maxPidExpirationMs,
      producerIdExpirationCheckIntervalMs = LogManager.ProducerIdExpirationCheckIntervalMs,
      scheduler = scheduler,
      time = time,
      brokerTopicStats = brokerTopicStats,
      logDirFailureChannel = logDirFailureChannel)

    if (logDir.getName.endsWith(Log.DeleteDirSuffix)) {
      addLogToBeDeleted(log)
    } else {
      val previous = {
        if (log.isFuture)
          this.futureLogs.put(topicPartition, log)
        else
          this.currentLogs.put(topicPartition, log)
      }
      if (previous != null) {
        if (log.isFuture)
          throw new IllegalStateException("Duplicate log directories found: %s, %s!".format(log.dir.getAbsolutePath, previous.dir.getAbsolutePath))
        else
          throw new IllegalStateException(s"Duplicate log directories for $topicPartition are found in both ${log.dir.getAbsolutePath} " +
            s"and ${previous.dir.getAbsolutePath}. It is likely because log directory failure happened while broker was " +
            s"replacing current replica with future replica. Recover broker from this failure by manually deleting one of the two directories " +
            s"for this partition. It is recommended to delete the partition in the log directory that is known to have failed recently.")
      }
    }
  }

  /**
   * Recover and load all logs in the given data directories
   */
  private def loadLogs(): Unit = {
    info("Loading logs.")
    val startMs = time.milliseconds
    //线程池
    val threadPools = ArrayBuffer.empty[ExecutorService]
    val offlineDirs = mutable.Set.empty[(String, IOException)]
    val jobs = mutable.Map.empty[File, Seq[Future[_]]]

    //todo: 遍历
    for (dir <- liveLogDirs) {
      try {
        //todo: 对每个日志目录创建numRecoveryThreadsPerDataDir(默认为1)个线程组成的线程池，并加入threadPools中
        val pool = Executors.newFixedThreadPool(numRecoveryThreadsPerDataDir)
        threadPools.append(pool)

        //检查上一次关闭是否是正常关闭
        val cleanShutdownFile = new File(dir, Log.CleanShutdownFile)

        // 检查.kafka_cleanshutdown文件是否存在
        // 存在则表示Kafka正在经历清理性的停机工作，此时跳过从本文件夹恢复日志
        if (cleanShutdownFile.exists) {
          debug(s"Found clean shutdown file. Skipping recovery for all logs in data directory: ${dir.getAbsolutePath}")
        } else {
          // log recovery itself is being performed by `Log` class during initialization
          brokerState.newState(RecoveringFromUncleanShutdown)
        }
        //读取日志检查点
        var recoveryPoints = Map[TopicPartition, Long]()
        try {
          // 从检查点文件读取Topic对应的恢复点offset信息
          // 文件为recovery-point-offset-checkpoint
          // checkpoint file format:
          // line1 : version
          // line2 : expectedSize
          // nlines: (tp, offset)
          recoveryPoints = this.recoveryPointCheckpoints(dir).read
        } catch {
          case e: Exception =>
            warn("Error occurred while reading recovery-point-offset-checkpoint file of directory " + dir, e)
            warn("Resetting the recovery checkpoint to 0")
        }

        var logStartOffsets = Map[TopicPartition, Long]()
        try {
          // 从检查点文件读取Topic对应的startoffset信息
          // 文件为log-start-offset-checkpoint
          // checkpoint file format:
          // line1 : version
          // line2 : expectedSize
          // nlines: (tp, startoffset)
          logStartOffsets = this.logStartOffsetCheckpoints(dir).read   //log-start-offset-checkpoint
        } catch {
          case e: Exception =>
            warn("Error occurred while reading log-start-offset-checkpoint file of directory " + dir, e)
        }
        // 每个日志子目录生成一个线程池的具体job，并提交到线程池中
        // 每个job的主要任务是通过loadLog()方法加载日志
        val jobsForDir = for {
          dirContent <- Option(dir.listFiles).toList
          logDir <- dirContent if logDir.isDirectory
        } yield {
          CoreUtils.runnable {
            try {
              //todo:  每个日志子目录生成一个job加载log，并且根据读取的recoveryPoints, logStartOffsets进行加载
              loadLog(logDir, recoveryPoints, logStartOffsets)
            } catch {
              case e: IOException =>
                offlineDirs.add((dir.getAbsolutePath, e))
                error("Error while loading log dir " + dir.getAbsolutePath, e)
            }
          }
        }
        //todo:  提交
        jobs(cleanShutdownFile) = jobsForDir.map(pool.submit)
      } catch {
        case e: IOException =>
          offlineDirs.add((dir.getAbsolutePath, e))
          error("Error while loading log dir " + dir.getAbsolutePath, e)
      }
    }

    try {
      // 等待所有日志加载的job完成，删除对应的cleanShutdownFile
      for ((cleanShutdownFile, dirJobs) <- jobs) {
        dirJobs.foreach(_.get)
        try {
          cleanShutdownFile.delete()
        } catch {
          case e: IOException =>
            offlineDirs.add((cleanShutdownFile.getParent, e))
            error(s"Error while deleting the clean shutdown file $cleanShutdownFile", e)
        }
      }

      offlineDirs.foreach { case (dir, e) =>
        logDirFailureChannel.maybeAddOfflineLogDir(dir, s"Error while deleting the clean shutdown file in dir $dir", e)
      }
    } catch {
      case e: ExecutionException =>
        error("There was an error in one of the threads during logs loading: " + e.getCause)
        throw e.getCause
    } finally {
      threadPools.foreach(_.shutdown())
    }

    info(s"Logs loading complete in ${time.milliseconds - startMs} ms.")
  }

  /**
   *  Start the background threads to flush logs and do log cleanup
   */
  def startup() {
    /* Schedule the cleanup task to delete old logs */
    //todo: 整体上定时调度了5个任务
    if (scheduler != null) {
      info("Starting log cleanup with a period of %d ms.".format(retentionCheckMs))
      //todo: 1. 启动 kafka-log-retention 周期性任务，对过期或过大的日志文件执行清理工作
      //遍历所有Log。负责清理未压缩的日志，清除条件【1.日志超过保留时间 2.日志大小超过保留大小】】
      scheduler.schedule("kafka-log-retention",
                         cleanupLogs _, //定时执行的方法
                         delay = InitialTaskDelayMs, //启动之后30s开始定时调度
                         period = retentionCheckMs,  //log.retention.check.interval.ms 默认为5分钟
                         TimeUnit.MILLISECONDS)
      info("Starting log flusher with a default period of %d ms.".format(flushCheckMs))

      //todo: 2. 启动 kafka-log-flusher 周期性任务，对日志文件执行刷盘操作,定时把内存中的数据刷到磁盘中
      scheduler.schedule("kafka-log-flusher",
                         flushDirtyLogs _, //定时执行的方法
                         delay = InitialTaskDelayMs,  //启动之后30s开始定时调度
                         period = flushCheckMs,  //log.flush.scheduler.interval.ms 默认值为Long.MaxValue
                         TimeUnit.MILLISECONDS)

      //todo: 3. 启动 kafka-recovery-point-checkpoint 周期性任务，更新 recovery-point-offset-checkpoint 文件
      //向路径中写入当前的恢复点，避免在重启时需要重新恢复全部数据
      scheduler.schedule("kafka-recovery-point-checkpoint",
                         checkpointLogRecoveryOffsets _, //定时执行的方法
                         delay = InitialTaskDelayMs,     //启动之后30s开始定时调度
                         period = flushRecoveryOffsetCheckpointMs, //log.flush.offset.checkpoint.interval.ms 默认为1分钟
                         TimeUnit.MILLISECONDS)

      //todo: 4. 启动 kafka-log-start-offset-checkpoint 周期性任务，更新 kafka-log-start-offset-checkpoint 文件
      //向日志目录写入当前存储的日志中的start offset。避免读到已经被删除的日志
      scheduler.schedule("kafka-log-start-offset-checkpoint",
                         checkpointLogStartOffsets _, //定时执行的方法
                         delay = InitialTaskDelayMs, //启动之后30s开始定时调度
                         period = flushStartOffsetCheckpointMs, //log.flush.start.offset.checkpoint.interval.ms 默认为1分钟
                         TimeUnit.MILLISECONDS)

      //todo: 5. 启动 kafka-delete-logs 周期性任务，清理已经被标记为删除delete的日志
      scheduler.schedule("kafka-delete-logs", // will be rescheduled after each delete logs with a dynamic period
                         deleteLogs _,   //定时执行的方法
                         delay = InitialTaskDelayMs, //启动之后30s开始定时调度
                         unit = TimeUnit.MILLISECONDS)
    }
    //启动日志清理组件
    if (cleanerConfig.enableCleaner)
      cleaner.startup()
  }

  /**
   * Close all the logs
   */
  def shutdown() {
    info("Shutting down.")

    removeMetric("OfflineLogDirectoryCount")
    for (dir <- logDirs) {
      removeMetric("LogDirectoryOffline", Map("logDirectory" -> dir.getAbsolutePath))
    }

    val threadPools = ArrayBuffer.empty[ExecutorService]
    val jobs = mutable.Map.empty[File, Seq[Future[_]]]

    // stop the cleaner first
    if (cleaner != null) {
      CoreUtils.swallow(cleaner.shutdown(), this)
    }

    // close logs in each dir
    for (dir <- liveLogDirs) {
      debug("Flushing and closing logs at " + dir)

      val pool = Executors.newFixedThreadPool(numRecoveryThreadsPerDataDir)
      threadPools.append(pool)

      val logsInDir = logsByDir.getOrElse(dir.toString, Map()).values

      val jobsForDir = logsInDir map { log =>
        CoreUtils.runnable {
          // flush the log to ensure latest possible recovery point
          log.flush()
          log.close()
        }
      }

      jobs(dir) = jobsForDir.map(pool.submit).toSeq
    }

    try {
      for ((dir, dirJobs) <- jobs) {
        dirJobs.foreach(_.get)

        // update the last flush point
        debug("Updating recovery points at " + dir)
        checkpointLogRecoveryOffsetsInDir(dir)

        debug("Updating log start offsets at " + dir)
        checkpointLogStartOffsetsInDir(dir)

        // mark that the shutdown was clean by creating marker file
        debug("Writing clean shutdown marker at " + dir)
        CoreUtils.swallow(Files.createFile(new File(dir, Log.CleanShutdownFile).toPath), this)
      }
    } catch {
      case e: ExecutionException =>
        error("There was an error in one of the threads during LogManager shutdown: " + e.getCause)
        throw e.getCause
    } finally {
      threadPools.foreach(_.shutdown())
      // regardless of whether the close succeeded, we need to unlock the data directories
      dirLocks.foreach(_.destroy())
    }

    info("Shutdown complete.")
  }

  /**
   * Truncate the partition logs to the specified offsets and checkpoint the recovery point to this offset
   *
   * @param partitionOffsets Partition logs that need to be truncated
   * @param isFuture True iff the truncation should be performed on the future log of the specified partitions
   */
  def truncateTo(partitionOffsets: Map[TopicPartition, Long], isFuture: Boolean) {
    var truncated = false
    for ((topicPartition, truncateOffset) <- partitionOffsets) {
      val log = {
        if (isFuture)
          futureLogs.get(topicPartition)
        else
          currentLogs.get(topicPartition)
      }
      // If the log does not exist, skip it
      if (log != null) {
        //May need to abort and pause the cleaning of the log, and resume after truncation is done.
        val needToStopCleaner = cleaner != null && truncateOffset < log.activeSegment.baseOffset
        if (needToStopCleaner && !isFuture)
          cleaner.abortAndPauseCleaning(topicPartition)
        try {
          if (log.truncateTo(truncateOffset))
            truncated = true
          if (needToStopCleaner && !isFuture)
            cleaner.maybeTruncateCheckpoint(log.dir.getParentFile, topicPartition, log.activeSegment.baseOffset)
        } finally {
          if (needToStopCleaner && !isFuture) {
            cleaner.resumeCleaning(Seq(topicPartition))
            info(s"Compaction for partition $topicPartition is resumed")
          }
        }
      }
    }

    if (truncated)
      checkpointLogRecoveryOffsets()
  }

  /**
   * Delete all data in a partition and start the log at the new offset
   *
   * @param topicPartition The partition whose log needs to be truncated
   * @param newOffset The new offset to start the log with
   * @param isFuture True iff the truncation should be performed on the future log of the specified partition
   */
  def truncateFullyAndStartAt(topicPartition: TopicPartition, newOffset: Long, isFuture: Boolean) {
    val log = {
      if (isFuture)
        futureLogs.get(topicPartition)
      else
        currentLogs.get(topicPartition)
    }
    // If the log does not exist, skip it
    if (log != null) {
        //Abort and pause the cleaning of the log, and resume after truncation is done.
      if (cleaner != null && !isFuture)
        cleaner.abortAndPauseCleaning(topicPartition)
      try {
        log.truncateFullyAndStartAt(newOffset)
        if (cleaner != null && !isFuture) {
          cleaner.maybeTruncateCheckpoint(log.dir.getParentFile, topicPartition, log.activeSegment.baseOffset)
        }
      } finally {
        if (cleaner != null && !isFuture) {
          cleaner.resumeCleaning(Seq(topicPartition))
          info(s"Compaction for partition $topicPartition is resumed")
        }
      }
      checkpointLogRecoveryOffsetsInDir(log.dir.getParentFile)
    }
  }

  /**
   * Write out the current recovery point for all logs to a text file in the log directory
   * to avoid recovering the whole log on startup.
   */
  def checkpointLogRecoveryOffsets() {
    liveLogDirs.foreach(checkpointLogRecoveryOffsetsInDir)
  }

  /**
   * Write out the current log start offset for all logs to a text file in the log directory
   * to avoid exposing data that have been deleted by DeleteRecordsRequest
   */
  def checkpointLogStartOffsets() {
    liveLogDirs.foreach(checkpointLogStartOffsetsInDir)
  }

  /**
   * Make a checkpoint for all logs in provided directory.
   */
  private def checkpointLogRecoveryOffsetsInDir(dir: File): Unit = {
    for {
      //遍历每个数据存储目录
      partitionToLog <- logsByDir.get(dir.getAbsolutePath)
    //获取目录中的 recovery-point-offset-checkpoint文件
      checkpoint <- recoveryPointCheckpoints.get(dir)
    } {
      try {
        //最新的offset数据写入到对应的文件中
        checkpoint.write(partitionToLog.mapValues(_.recoveryPoint))
        allLogs.foreach(_.deleteSnapshotsAfterRecoveryPointCheckpoint())
      } catch {
        case e: IOException =>
          logDirFailureChannel.maybeAddOfflineLogDir(dir.getAbsolutePath, s"Disk error while writing to recovery point " +
            s"file in directory $dir", e)
      }
    }
  }

  /**
   * Checkpoint log start offset for all logs in provided directory.
   */
  private def checkpointLogStartOffsetsInDir(dir: File): Unit = {
    for {
      // 历每个数据存储目录
      partitionToLog <- logsByDir.get(dir.getAbsolutePath)
      //获取目录中的log-start-offset-checkpoint文件
      checkpoint <- logStartOffsetCheckpoints.get(dir)
    } {
      try {
        //获取此时的logStartOffsets
        val logStartOffsets = partitionToLog.filter { case (_, log) =>
          log.logStartOffset > log.logSegments.head.baseOffset
        }.mapValues(_.logStartOffset)
        //logStartOffsets写入到对应的文件中
        checkpoint.write(logStartOffsets)
      } catch {
        case e: IOException =>
          logDirFailureChannel.maybeAddOfflineLogDir(dir.getAbsolutePath, s"Disk error while writing to logStartOffset file in directory $dir", e)
      }
    }
  }

  // The logDir should be an absolute path
  def maybeUpdatePreferredLogDir(topicPartition: TopicPartition, logDir: String): Unit = {
    // Do not cache the preferred log directory if either the current log or the future log for this partition exists in the specified logDir
    if (!getLog(topicPartition).exists(_.dir.getParent == logDir) &&
        !getLog(topicPartition, isFuture = true).exists(_.dir.getParent == logDir))
      preferredLogDirs.put(topicPartition, logDir)
  }

  def abortAndPauseCleaning(topicPartition: TopicPartition): Unit = {
    if (cleaner != null)
      cleaner.abortAndPauseCleaning(topicPartition)
  }


  /**
   * Get the log if it exists, otherwise return None
   *
   * @param topicPartition the partition of the log
   * @param isFuture True iff the future log of the specified partition should be returned
   */
  def getLog(topicPartition: TopicPartition, isFuture: Boolean = false): Option[Log] = {
    if (isFuture)
      Option(futureLogs.get(topicPartition))
    else
      Option(currentLogs.get(topicPartition))
  }

  /**
   * If the log already exists, just return a copy of the existing log
   * Otherwise if isNew=true or if there is no offline log directory, create a log for the given topic and the given partition
   * Otherwise throw KafkaStorageException
   *
   * @param topicPartition The partition whose log needs to be returned or created
   * @param config The configuration of the log that should be applied for log creation
   * @param isNew Whether the replica should have existed on the broker or not
   * @param isFuture True iff the future log of the specified partition should be returned or created
   * @throws KafkaStorageException if isNew=false, log is not found in the cache and there is offline log directory on the broker
   */
  def getOrCreateLog(topicPartition: TopicPartition, config: LogConfig, isNew: Boolean = false, isFuture: Boolean = false): Log = {
    logCreationOrDeletionLock synchronized {
      getLog(topicPartition, isFuture).getOrElse {
        // create the log if it has not already been created in another thread
        if (!isNew && offlineLogDirs.nonEmpty)
          throw new KafkaStorageException(s"Can not create log for $topicPartition because log directories ${offlineLogDirs.mkString(",")} are offline")

        val logDir = {
          val preferredLogDir = preferredLogDirs.get(topicPartition)

          if (isFuture) {
            if (preferredLogDir == null)
              throw new IllegalStateException(s"Can not create the future log for $topicPartition without having a preferred log directory")
            else if (getLog(topicPartition).get.dir.getParent == preferredLogDir)
              throw new IllegalStateException(s"Can not create the future log for $topicPartition in the current log directory of this partition")
          }

          if (preferredLogDir != null)
            preferredLogDir
          else
            nextLogDir().getAbsolutePath
        }
        if (!isLogDirOnline(logDir))
          throw new KafkaStorageException(s"Can not create log for $topicPartition because log directory $logDir is offline")

        try {
          val dir = {
            if (isFuture)
              new File(logDir, Log.logFutureDirName(topicPartition))
            else
              new File(logDir, Log.logDirName(topicPartition))
          }
          Files.createDirectories(dir.toPath)

          val log = Log(
            dir = dir,
            config = config,
            logStartOffset = 0L,
            recoveryPoint = 0L,
            maxProducerIdExpirationMs = maxPidExpirationMs,
            producerIdExpirationCheckIntervalMs = LogManager.ProducerIdExpirationCheckIntervalMs,
            scheduler = scheduler,
            time = time,
            brokerTopicStats = brokerTopicStats,
            logDirFailureChannel = logDirFailureChannel)

          if (isFuture)
            futureLogs.put(topicPartition, log)
          else
            currentLogs.put(topicPartition, log)

          info(s"Created log for partition $topicPartition in $logDir with properties " +
            s"{${config.originals.asScala.mkString(", ")}}.")
          // Remove the preferred log dir since it has already been satisfied
          preferredLogDirs.remove(topicPartition)

          log
        } catch {
          case e: IOException =>
            val msg = s"Error while creating log for $topicPartition in dir $logDir"
            logDirFailureChannel.maybeAddOfflineLogDir(logDir, msg, e)
            throw new KafkaStorageException(msg, e)
        }
      }
    }
  }

  /**
   *  Delete logs marked for deletion. Delete all logs for which `currentDefaultConfig.fileDeleteDelayMs`
   *  has elapsed after the delete was scheduled. Logs for which this interval has not yet elapsed will be
   *  considered for deletion in the next iteration of `deleteLogs`. The next iteration will be executed
   *  after the remaining time for the first log that is not deleted. If there are no more `logsToBeDeleted`,
   *  `deleteLogs` will be executed after `currentDefaultConfig.fileDeleteDelayMs`.
   */
  private def deleteLogs(): Unit = {
    var nextDelayMs = 0L
    try {
      def nextDeleteDelayMs: Long = {
        if (!logsToBeDeleted.isEmpty) {
          val (_, scheduleTimeMs) = logsToBeDeleted.peek()
          scheduleTimeMs + currentDefaultConfig.fileDeleteDelayMs - time.milliseconds()
        } else
          currentDefaultConfig.fileDeleteDelayMs
      }

      while ({nextDelayMs = nextDeleteDelayMs; nextDelayMs <= 0}) {
        val (removedLog, _) = logsToBeDeleted.take()
        if (removedLog != null) {
          try {
            removedLog.delete()
            info(s"Deleted log for partition ${removedLog.topicPartition} in ${removedLog.dir.getAbsolutePath}.")
          } catch {
            case e: KafkaStorageException =>
              error(s"Exception while deleting $removedLog in dir ${removedLog.dir.getParent}.", e)
          }
        }
      }
    } catch {
      case e: Throwable =>
        error(s"Exception in kafka-delete-logs thread.", e)
    } finally {
      try {
        scheduler.schedule("kafka-delete-logs",
          deleteLogs _,
          delay = nextDelayMs,
          unit = TimeUnit.MILLISECONDS)
      } catch {
        case e: Throwable =>
          if (scheduler.isStarted) {
            // No errors should occur unless scheduler has been shutdown
            error(s"Failed to schedule next delete in kafka-delete-logs thread", e)
          }
      }
    }
  }

  /**
    * Mark the partition directory in the source log directory for deletion and
    * rename the future log of this partition in the destination log directory to be the current log
    *
    * @param topicPartition TopicPartition that needs to be swapped
    */
  def replaceCurrentWithFutureLog(topicPartition: TopicPartition): Unit = {
    logCreationOrDeletionLock synchronized {
      val sourceLog = currentLogs.get(topicPartition)
      val destLog = futureLogs.get(topicPartition)

      if (sourceLog == null)
        throw new KafkaStorageException(s"The current replica for $topicPartition is offline")
      if (destLog == null)
        throw new KafkaStorageException(s"The future replica for $topicPartition is offline")

      destLog.renameDir(Log.logDirName(topicPartition))
      // Now that future replica has been successfully renamed to be the current replica
      // Update the cached map and log cleaner as appropriate.
      futureLogs.remove(topicPartition)
      currentLogs.put(topicPartition, destLog)
      if (cleaner != null) {
        cleaner.alterCheckpointDir(topicPartition, sourceLog.dir.getParentFile, destLog.dir.getParentFile)
        cleaner.resumeCleaning(Seq(topicPartition))
        info(s"Compaction for partition $topicPartition is resumed")
      }

      try {
        sourceLog.renameDir(Log.logDeleteDirName(topicPartition))
        // Now that replica in source log directory has been successfully renamed for deletion.
        // Close the log, update checkpoint files, and enqueue this log to be deleted.
        sourceLog.close()
        checkpointLogRecoveryOffsetsInDir(sourceLog.dir.getParentFile)
        checkpointLogStartOffsetsInDir(sourceLog.dir.getParentFile)
        addLogToBeDeleted(sourceLog)
      } catch {
        case e: KafkaStorageException =>
          // If sourceLog's log directory is offline, we need close its handlers here.
          // handleLogDirFailure() will not close handlers of sourceLog because it has been removed from currentLogs map
          sourceLog.closeHandlers()
          sourceLog.removeLogMetrics()
          throw e
      }

      info(s"The current replica is successfully replaced with the future replica for $topicPartition")
    }
  }

  /**
    * Rename the directory of the given topic-partition "logdir" as "logdir.uuid.delete" and
    * add it in the queue for deletion.
    *
    * @param topicPartition TopicPartition that needs to be deleted
    * @param isFuture True iff the future log of the specified partition should be deleted
    * @return the removed log
    */
  def asyncDelete(topicPartition: TopicPartition, isFuture: Boolean = false): Log = {
    val removedLog: Log = logCreationOrDeletionLock synchronized {
      if (isFuture)
        futureLogs.remove(topicPartition)
      else
        currentLogs.remove(topicPartition)
    }
    if (removedLog != null) {
      //We need to wait until there is no more cleaning task on the log to be deleted before actually deleting it.
      if (cleaner != null && !isFuture) {
        cleaner.abortCleaning(topicPartition)
        cleaner.updateCheckpoints(removedLog.dir.getParentFile)
      }
      removedLog.renameDir(Log.logDeleteDirName(topicPartition))
      checkpointLogRecoveryOffsetsInDir(removedLog.dir.getParentFile)
      checkpointLogStartOffsetsInDir(removedLog.dir.getParentFile)
      addLogToBeDeleted(removedLog)
      info(s"Log for partition ${removedLog.topicPartition} is renamed to ${removedLog.dir.getAbsolutePath} and is scheduled for deletion")
    } else if (offlineLogDirs.nonEmpty) {
      throw new KafkaStorageException(s"Failed to delete log for ${if (isFuture) "future" else ""} $topicPartition because it may be in one of the offline directories ${offlineLogDirs.mkString(",")}")
    }
    removedLog
  }

  /**
   * Choose the next directory in which to create a log. Currently this is done
   * by calculating the number of partitions in each directory and then choosing the
   * data directory with the fewest partitions.
   */
  private def nextLogDir(): File = {
    if(_liveLogDirs.size == 1) {
      _liveLogDirs.peek()
    } else {
      // count the number of logs in each parent directory (including 0 for empty directories
      val logCounts = allLogs.groupBy(_.dir.getParent).mapValues(_.size)
      val zeros = _liveLogDirs.asScala.map(dir => (dir.getPath, 0)).toMap
      val dirCounts = (zeros ++ logCounts).toBuffer

      // choose the directory with the least logs in it
      val leastLoaded = dirCounts.sortBy(_._2).head
      new File(leastLoaded._1)
    }
  }

  /**
   * Delete any eligible logs. Return the number of segments deleted.
   * Only consider logs that are not compacted.
   */
  def cleanupLogs() {
    debug("Beginning log cleanup...")
    var total = 0
    val startMs = time.milliseconds

    // clean current logs.
    // todo: 遍历处理每个 topic 分区对应的 Log 对象，只有对应 Log 配置了 cleanup.policy=delete 才会执行删除
    val deletableLogs = {
      if (cleaner != null) {
        // prevent cleaner from working on same partitions when changing cleanup policy
        cleaner.pauseCleaningForNonCompactedPartitions()
      } else {
        currentLogs.filter {
          case (_, log) => !log.config.compact
        }
      }
    }

    try {
      deletableLogs.foreach {
        case (topicPartition, log) =>
          debug("Garbage collecting '" + log.name + "'")
          //todo 遍历删除当前 Log 对象中过期的 LogSegment 对象，并保证 Log 的大小在允许范围内（对应 retention.bytes 配置）
          total += log.deleteOldSegments()

          val futureLog = futureLogs.get(topicPartition)
          if (futureLog != null) {
            // clean future logs
            debug("Garbage collecting future log '" + futureLog.name + "'")
            total += futureLog.deleteOldSegments()
          }
      }
    } finally {
      if (cleaner != null) {
        cleaner.resumeCleaning(deletableLogs.map(_._1))
      }
    }

    debug("Log cleanup completed. " + total + " files deleted in " +
                  (time.milliseconds - startMs) / 1000 + " seconds")
  }

  /**
   * Get all the partition logs
   */
  def allLogs: Iterable[Log] = currentLogs.values ++ futureLogs.values

  def logsByTopic(topic: String): Seq[Log] = {
    (currentLogs.toList ++ futureLogs.toList).filter { case (topicPartition, _) =>
      topicPartition.topic() == topic
    }.map { case (_, log) => log }
  }

  /**
   * Map of log dir to logs by topic and partitions in that dir
   */
  private def logsByDir: Map[String, Map[TopicPartition, Log]] = {
    (this.currentLogs.toList ++ this.futureLogs.toList).groupBy {
      case (_, log) => log.dir.getParent
    }.mapValues(_.toMap)
  }

  // logDir should be an absolute path
  def isLogDirOnline(logDir: String): Boolean = {
    // The logDir should be an absolute path
    if (!logDirs.exists(_.getAbsolutePath == logDir))
      throw new LogDirNotFoundException(s"Log dir $logDir is not found in the config.")

    _liveLogDirs.contains(new File(logDir))
  }

  /**
   * Flush any log which has exceeded its flush interval and has unwritten messages.
   */
  private def flushDirtyLogs(): Unit = {
    debug("Checking for dirty logs to flush...")

    for ((topicPartition, log) <- currentLogs.toList ++ futureLogs.toList) {
      try {
        //todo: 距离上一次刷盘的时间
        val timeSinceLastFlush = time.milliseconds - log.lastFlushTime
        debug("Checking if flush is needed on " + topicPartition.topic + " flush interval  " + log.config.flushMs +
              " last flushed " + log.lastFlushTime + " time since last flush: " + timeSinceLastFlush)
        //todo: 已经超过周期刷盘时间间隔(Long类型的最大值)
        //也就意味着kafka不会主动把内存的数据周期性刷到磁盘中
        //把内存数据刷写到磁盘是由操作系统自己完成的，当然可以自己去配置这个值
        if(timeSinceLastFlush >= log.config.flushMs)
          log.flush
      } catch {
        case e: Throwable =>
          error("Error flushing topic " + topicPartition.topic, e)
      }
    }
  }
}

object LogManager {

  val RecoveryPointCheckpointFile = "recovery-point-offset-checkpoint"
  val LogStartOffsetCheckpointFile = "log-start-offset-checkpoint"
  val ProducerIdExpirationCheckIntervalMs = 10 * 60 * 1000

  def apply(config: KafkaConfig,
            initialOfflineDirs: Seq[String],
            zkClient: KafkaZkClient,
            brokerState: BrokerState,
            kafkaScheduler: KafkaScheduler,
            time: Time,
            brokerTopicStats: BrokerTopicStats,
            logDirFailureChannel: LogDirFailureChannel): LogManager = {
    //加载默认配置
    val defaultProps = KafkaServer.copyKafkaConfigToLog(config)
    val defaultLogConfig = LogConfig(defaultProps)

    // read the log configurations from zookeeper
    //从zk获取各个topic的相关配置
    val (topicConfigs, failed) = zkClient.getLogConfigs(zkClient.getAllTopicsInCluster, defaultProps)
    if (!failed.isEmpty) throw failed.head._2

    //日志清理组件配置
    val cleanerConfig = LogCleaner.cleanerConfig(config)

    //todo: 创建LogManager对象 ，该类的主构造代码都会被运行
    //todo: 内部有一个重要的参数：logDirs = config.logDirs.map(new File(_))
    //配置了kafka存储的目录参数：log.dirs  ，在生产环境中通常会对应多个目录
    new LogManager(logDirs = config.logDirs.map(new File(_).getAbsoluteFile),
      initialOfflineDirs = initialOfflineDirs.map(new File(_).getAbsoluteFile),
      topicConfigs = topicConfigs,
      initialDefaultConfig = defaultLogConfig,
      cleanerConfig = cleanerConfig,
      recoveryThreadsPerDataDir = config.numRecoveryThreadsPerDataDir,
      flushCheckMs = config.logFlushSchedulerIntervalMs,
      flushRecoveryOffsetCheckpointMs = config.logFlushOffsetCheckpointIntervalMs,
      flushStartOffsetCheckpointMs = config.logFlushStartOffsetCheckpointIntervalMs,
      retentionCheckMs = config.logCleanupIntervalMs,
      maxPidExpirationMs = config.transactionIdExpirationMs,
      scheduler = kafkaScheduler,
      brokerState = brokerState,
      brokerTopicStats = brokerTopicStats,
      logDirFailureChannel = logDirFailureChannel,
      time = time)
  }
}
