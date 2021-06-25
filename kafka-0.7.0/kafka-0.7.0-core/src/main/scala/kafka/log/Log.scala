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

package kafka.log

import java.util.concurrent.atomic._
import java.text.NumberFormat
import java.io._

import org.apache.log4j._
import kafka.message._
import kafka.utils._
import kafka.common._
import kafka.api.OffsetRequest
import java.util._

private[log] object Log {
  val FileSuffix = ".kafka"

  /**
    * Find a given range object in a list of ranges by a value in that range. Does a binary search over the ranges
    * but instead of checking for equality looks within the range. Takes the array size as an option in case
    * the array grows while searching happens
    *
    * TODO: This should move into SegmentList.scala
    */
  def findRange[T <: Range](ranges: Array[T], value: Long, arraySize: Int): Option[T] = {
    if (ranges.size < 1)
      return None

    // check out of bounds
    if (value < ranges(0).start || value > ranges(arraySize - 1).start + ranges(arraySize - 1).size)
      throw new OffsetOutOfRangeException("offset " + value + " is out of range")

    // check at the end
    if (value == ranges(arraySize - 1).start + ranges(arraySize - 1).size)
      return None

    var low = 0
    var high = arraySize - 1
    while (low <= high) {
      val mid = (high + low) / 2
      val found = ranges(mid)
      if (found.contains(value))
        return Some(found)
      else if (value < found.start)
        high = mid - 1
      else
        low = mid + 1
    }
    None
  }

  def findRange[T <: Range](ranges: Array[T], value: Long): Option[T] =
    findRange(ranges, value, ranges.length)

  /**
    * Make log segment file name from offset bytes. All this does is pad out the offset number with zeros
    * so that ls sorts the files numerically
    */
  def nameFromOffset(offset: Long): String = {
    val nf = NumberFormat.getInstance()
    nf.setMinimumIntegerDigits(20)
    nf.setMaximumFractionDigits(0)
    nf.setGroupingUsed(false)
    val fileName = nf.format(offset) + Log.FileSuffix
    println("-----------------nameFromOffset---------------------------fileName-" + fileName)
    fileName
  }
}

/**
  * A segment file in the log directory. Each log semgment consists of an open message set, a start offset and a size
  */
private[log] class LogSegment(val file: File, val messageSet: FileMessageSet, val start: Long) extends Range {
  @volatile var deleted = false

  def size: Long = messageSet.highWaterMark

  override def toString() = "(file=" + file + ", start=" + start + ", size=" + size + ")"
}


/**
  * An append-only log for storing messages.
  */
@threadsafe
private[log] class Log(val dir: File, val maxSize: Long, val flushInterval: Int, val needRecovery: Boolean) {

  private val logger = Logger.getLogger(classOf[Log])

  /* A lock that guards all modifications to the log */
  private val lock = new Object

  /* The current number of unflushed messages appended to the write */
  private val unflushed = new AtomicInteger(0)

  /* last time it was flushed */
  private val lastflushedTime = new AtomicLong(System.currentTimeMillis)

  /* The actual segments of the log */
  private[log] val segments: SegmentList[LogSegment] = loadSegments() // 加载已经存在的日志分片文件，加载元数据信息

  /* The name of this log */
  val name = dir.getName()

  private val logStats = new LogStats(this)

  Utils.registerMBean(logStats, "kafka:type=kafka.logs." + dir.getName)

  /* Load the log segments from the log files on disk */
  private def loadSegments(): SegmentList[LogSegment] = {
    println("-------------Log-------------------loadSegments-----------start-------dir=" + dir)
    // open all the segments read-only
    val accum = new ArrayList[LogSegment]
    val ls = dir.listFiles() // 获取这个目录的所有文件
    if (ls != null) {
      for (file <- ls if file.isFile && file.toString.endsWith(Log.FileSuffix)) { // 如果是文件，并且后缀是.kafka
        if (!file.canRead) {
          throw new IOException("Could not read file " + file)
        }
        val filename = file.getName()
        val start = filename.substring(0, filename.length - Log.FileSuffix.length).toLong
        println("-------------Log-------------------loadSegments--------filename =" + filename + "-----start-------" + start)
        val messageSet = new FileMessageSet(file, false) // mutable=false 默认不可变
        accum.add(new LogSegment(file, messageSet, start)) // 一个文件创建一个 LogSegment 对象
      }
    }

    if (accum.size == 0) { // 如果没有日志文件
      // no existing segments, create a new mutable segment
      val newFile = new File(dir, Log.nameFromOffset(0)) // 生成一个新的文件
    val set = new FileMessageSet(newFile, true) // mutable=true可变的
      accum.add(new LogSegment(newFile, set, 0))
    } else { // 反之，对集合进行排序
      // there is at least one existing segment, validate and recover them/it
      // sort segments into ascending order for fast searching
      Collections.sort(accum, new Comparator[LogSegment] { // 根据start，start就是日志文件名称上的索引位置，进行排序
        def compare(s1: LogSegment, s2: LogSegment): Int = {
          if (s1.start == s2.start) 0
          else if (s1.start < s2.start) -1
          else 1
        }
      })
      validateSegments(accum) // 检验日志片段

      //make the final section mutable and run recovery on it if necessary
      val last = accum.remove(accum.size - 1)
      println("-------------Log-------------------loadSegments-----------end-----移除的最后文件--" + last.file.getAbsolutePath())
      last.messageSet.close()
      logger.info("Loading the last segment " + last.file.getAbsolutePath() + " in mutable mode, recovery " + needRecovery)
      val mutable = new LogSegment(last.file, new FileMessageSet(last.file, true, new AtomicBoolean(needRecovery)), last.start)
      accum.add(mutable)
    }
    println("-------------Log-------------------loadSegments-----------end-------")
    new SegmentList(accum.toArray(new Array[LogSegment](accum.size)))
  }

  /**
    * Check that the ranges and sizes add up, otherwise we have lost some data somewhere
    */
  private def validateSegments(segments: ArrayList[LogSegment]) {
    lock synchronized {
      for (i <- 0 until segments.size - 1) {
        val curr = segments.get(i)
        val next = segments.get(i + 1)
        if (curr.start + curr.size != next.start)
          throw new IllegalStateException("The following segments don't validate: " +
            curr.file.getAbsolutePath() + ", " + next.file.getAbsolutePath())
      }
    }
  }

  /**
    * The number of segments in the log
    */
  def numberOfSegments: Int = segments.view.length

  /**
    * Close this log
    */
  def close() {
    lock synchronized {
      for (seg <- segments.view)
        seg.messageSet.close()
    }
  }

  /**
    * Append this message set to the active segment of the log, rolling over to a fresh segment if necessary.
    * Returns the offset at which the messages are written.
    */
  def append(messages: MessageSet): Unit = {
    println("-------------Log-------------------append----------追加数据-------start-------")
    // validate the messages
    println("-------------Log-------------------append-----------验证消息开始-------")
    var numberOfMessages = 0
    for (messageAndOffset <- messages) {
      if (!messageAndOffset.message.isValid) { //检查消息是否有效
        throw new InvalidMessageException()
      }
      numberOfMessages += 1; // 对记录数计数
    }
    println("-------------Log-------------------append-----------验证消息结束-------numberOfMessages=" + numberOfMessages)
    logStats.recordAppendedMessages(numberOfMessages) // 设置消息条数

    // they are valid, insert them in the log  如果消息是有效的，就将消息插入到log分片中
    lock synchronized {
      val segment = segments.view.last // 获取最后的 segments，将数据追加在最后的segment上面
      val fileMsgSet: FileMessageSet = segment.messageSet
      fileMsgSet.append(messages)
      maybeFlush(numberOfMessages) // 可能需要刷新
      maybeRoll(segment) // 日志分片 可能需要日志回滚
    }
    println("-------------Log-------------------append----------追加数据-----------end-------")
  }

  /**
    * Read from the log file at the given offset
    */
  def read(offset: Long, length: Int): MessageSet = {
    val view = segments.view
    Log.findRange(view, offset, view.length) match {
      case Some(segment) => segment.messageSet.read((offset - segment.start), length)
      case _ => MessageSet.Empty
    }
  }

  /**
    * Delete any log segments matching the given predicate function
    */
  def markDeletedWhile(predicate: LogSegment => Boolean): Seq[LogSegment] = {
    println("-------------Log-------------------markDeletedWhile-----------start-------")
    lock synchronized {
      val view = segments.view
      val deletable = view.takeWhile(predicate)
      for (seg <- deletable)
        seg.deleted = true
      val numToDelete = deletable.size
      // if we are deleting everything, create a new empty segment
      if (numToDelete == view.size) {
        roll()
      }
      val res = segments.trunc(numToDelete)
      println("-------------Log-------------------markDeletedWhile-----------end-------")
      res
    }
  }

  /**
    * Get the size of the log in bytes
    */
  def size: Long =
    segments.view.foldLeft(0L)(_ + _.size)

  /**
    * The byte offset of the message that will be appended next.
    */
  def nextAppendOffset(): Long = {
    flush
    val last = segments.view.last //获取最后一个日志分段
    val newOffset = last.start + last.size
    println("-------------Log-------------------nextAppendOffset-----------------下一个 newOffset=" + newOffset)
    newOffset
  }

  /**
    * get the current high watermark of the log
    */
  def getHighwaterMark: Long = segments.view.last.messageSet.highWaterMark

  /**
    * Roll the log over if necessary
    */
  private def maybeRoll(segment: LogSegment) {
    println("-------------Log-------------------maybeRoll-----------------segment.size= " + segment.size)
    if (segment.messageSet.sizeInBytes > maxSize) { // 判断日志分片的大小，是否超过设置的大小
      roll()
    }
  }

  /**
    * Create a new segment and make it active
    */
  def roll() {
    println("-------------Log-------------------roll-----------------最大文件大小-maxSize=" + maxSize)
    lock synchronized {
      //val last = segments.view.last
      val newOffset = nextAppendOffset() // 生成的新的offset索引
      val newFile = new File(dir, Log.nameFromOffset(newOffset)) // 根据新的offset，生成新的文件
      if (logger.isDebugEnabled) {
        logger.debug("Rolling log '" + name + "' to " + newFile.getName())
      }
      Thread.sleep(3 * 100) // 如果休眠时间过长，例如30S，这里生成新的日志片段，造成数据丢失，不能继续保存数据
      println("-------------Log-------------------roll-----开始追加日志文件--------------------------------------")
      segments.append(new LogSegment(newFile, new FileMessageSet(newFile, true), newOffset))
      println("-------------Log-------------------roll-----打印segments--------------------------------------")
      for (logSegment <- segments.view) {
        println("-------------Log-----------------roll=" + logSegment.file.getAbsoluteFile)
      }
    }
  }

  /**
    * Flush the log if necessary
    */
  private def maybeFlush(numberOfMessages: Int) {
    println("-------------Log-------------------maybeFlush-----------------numberOfMessages-" + numberOfMessages)
    if (unflushed.addAndGet(numberOfMessages) >= flushInterval) { // 如果处理的消息条数，大于等于设置的条件，就执行flush
      flush()
    }
  }

  /**
    * Flush this log file to the physical disk
    */
  def flush(): Unit = {
    if (unflushed.get == 0) {
      return
    }

    lock synchronized {
      if (logger.isDebugEnabled) {
        logger.debug("Flushing log '" + name + "' last flushed: " + getLastFlushedTime + " current time: " + System.currentTimeMillis)
      }
      segments.view.last.messageSet.flush() // 对日志片段最后一个分片，进行flush
      unflushed.set(0)
      lastflushedTime.set(System.currentTimeMillis)
    }
  }

  def getOffsetsBefore(request: OffsetRequest): Array[Long] = {
    val segsArray = segments.view
    var offsetTimeArray: Array[Tuple2[Long, Long]] = null
    if (segsArray.last.size > 0)
      offsetTimeArray = new Array[Tuple2[Long, Long]](segsArray.length + 1)
    else
      offsetTimeArray = new Array[Tuple2[Long, Long]](segsArray.length)

    for (i <- 0 until segsArray.length)
      offsetTimeArray(i) = (segsArray(i).start, segsArray(i).file.lastModified)
    if (segsArray.last.size > 0)
      offsetTimeArray(segsArray.length) = (segsArray.last.start + segsArray.last.messageSet.highWaterMark, SystemTime.milliseconds)

    var startIndex = -1
    request.time match {
      case OffsetRequest.LatestTime =>
        startIndex = offsetTimeArray.length - 1
      case OffsetRequest.EarliestTime =>
        startIndex = 0
      case _ =>
        var isFound = false
        if (logger.isDebugEnabled) {
          logger.debug("Offset time array = " + offsetTimeArray.foreach(o => "%d, %d".format(o._1, o._2)))
        }
        startIndex = offsetTimeArray.length - 1
        while (startIndex >= 0 && !isFound) {
          if (offsetTimeArray(startIndex)._2 <= request.time)
            isFound = true
          else
            startIndex -= 1
        }
    }

    val retSize = request.maxNumOffsets.min(startIndex + 1)
    val ret = new Array[Long](retSize)
    for (j <- 0 until retSize) {
      ret(j) = offsetTimeArray(startIndex)._1
      startIndex -= 1
    }
    ret
  }

  def getTopicName(): String = {
    name.substring(0, name.lastIndexOf("-"))
  }

  def getLastFlushedTime(): Long = {
    return lastflushedTime.get
  }
}
  
