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

package kafka.message

import java.io._
import java.nio._
import java.nio.channels._
import java.util.concurrent.atomic._
import org.apache.log4j.Logger

import kafka.utils._

/**
  * An on-disk message set. The set can be opened either mutably or immutably. Mutation attempts
  * will fail on an immutable message set. An optional limit and offset can be applied to the message set
  * which will control the offset into the file and the effective length into the file from which
  * messages will be read
  */
@nonthreadsafe
class FileMessageSet private[kafka](private[message] val channel: FileChannel,
                                    private[message] val offset: Long,
                                    private[message] val limit: Long,
                                    val mutable: Boolean,
                                    val needRecover: AtomicBoolean) extends MessageSet {
  println("-----------------------------FileMessageSet---------------------------channel : " + channel)
  private val setSize = new AtomicLong()
  private val setHighWaterMark = new AtomicLong()
  private val logger = Logger.getLogger(classOf[FileMessageSet])

  if (mutable) {
    if (limit < Long.MaxValue || offset > 0)
      throw new IllegalArgumentException("Attempt to open a mutable message set with a view or offset, which is not allowed.")

    if (needRecover.get) { // 如果需要恢复
      // set the file position to the end of the file for appending messages
      val startMs = System.currentTimeMillis
      val truncated = recover()
      logger.info("Recovery succeeded in " + (System.currentTimeMillis - startMs) / 1000 + " seconds. " + truncated + " bytes truncated.")
    } else {
      setSize.set(channel.size())
      setHighWaterMark.set(sizeInBytes)
      channel.position(channel.size)
    }
  } else {
    println("----------------FileMessageSet----------------init----------------")
    val minSize = scala.math.min(channel.size(), limit)
    setSize.set(minSize - offset)
    setHighWaterMark.set(sizeInBytes)
    if (logger.isDebugEnabled){
      logger.debug("initializing high water mark in immutable mode: " + highWaterMark)
    }
  }

  /**
    * Create a file message set with no limit or offset
    */
  def this(channel: FileChannel, mutable: Boolean) =
    this(channel, 0, Long.MaxValue, mutable, new AtomicBoolean(false))

  /**
    * Create a file message set with no limit or offset
    */
  def this(file: File, mutable: Boolean) = {
    this(Utils.openChannel(file, mutable), mutable) //  打开channel 通道
  }

  /**
    * Create a file message set with no limit or offset
    */
  def this(channel: FileChannel, mutable: Boolean, needRecover: AtomicBoolean) =
    this(channel, 0, Long.MaxValue, mutable, needRecover)

  /**
    * Create a file message set with no limit or offset
    */
  def this(file: File, mutable: Boolean, needRecover: AtomicBoolean) =
    this(Utils.openChannel(file, mutable), mutable, needRecover)


  /**
    * Return a message set which is a view into this set starting from the given offset and with the given size limit.
    */
  def read(readOffset: Long, size: Long): MessageSet = {
    new FileMessageSet(channel, this.offset + readOffset, scala.math.min(this.offset + readOffset + size, highWaterMark),
      false, new AtomicBoolean(false))
  }

  /**
    * Write some of this set to the given channel, return the ammount written
    */
  def writeTo(destChannel: WritableByteChannel, writeOffset: Long, size: Long): Long = {
    println("-------------FileMessageSet-------------------writeTo--------------transferTo------")
    channel.transferTo(offset + writeOffset, scala.math.min(size, sizeInBytes), destChannel)
  }

  /**
    * Get an iterator over the messages in the set
    */
  override def iterator: Iterator[MessageAndOffset] = {
    println("--------------------------FileMessageSet-------------------iterator---------")
    new IteratorTemplate[MessageAndOffset] {
      var location = offset

      override def makeNext(): MessageAndOffset = {
        // read the size of the item
        val sizeBuffer = ByteBuffer.allocate(4)
        channel.read(sizeBuffer, location)
        if (sizeBuffer.hasRemaining)
          return allDone()

        sizeBuffer.rewind()
        val size: Int = sizeBuffer.getInt()
        if (size < Message.MinHeaderSize)
          return allDone()

        // read the item itself
        val buffer = ByteBuffer.allocate(size)
        channel.read(buffer, location + 4)
        if (buffer.hasRemaining)
          return allDone()
        buffer.rewind()

        // increment the location and return the item
        location += size + 4
        new MessageAndOffset(new Message(buffer), location)
      }
    }
  }

  /**
    * The number of bytes taken up by this file set
    */
  def sizeInBytes(): Long = setSize.get()

  /**
    * The high water mark
    */
  def highWaterMark(): Long = setHighWaterMark.get()

  def checkMutable(): Unit = {
    if (!mutable)
      throw new IllegalStateException("Attempt to invoke mutation on immutable message set.")
  }

  /**
    * Append this message to the message set
    */
  def append(messages: MessageSet): Unit = {
    println("-------------FileMessageSet-------------------append--------------start------messages： "+ messages)
    checkMutable()
    var written = 0L
    while (written < messages.sizeInBytes) {
      written += messages.writeTo(channel, 0, messages.sizeInBytes)
    }
    println("-------------FileMessageSet-------------------append--------------end----written="+ written)
    setSize.getAndAdd(written)
  }

  /**
    * Commit all written data to the physical disk 将所有写入的数据提交到物理磁盘
    */
  def flush() = {
    checkMutable()  // 检查是否可变
    val startTime = SystemTime.milliseconds

    /**
      * FileChannel.force()方法将通道里尚未写入磁盘的数据强制写到磁盘上。
      * 出于性能方面的考虑，操作系统会将数据缓存在内存中，所以无法保证写入到FileChannel里的数据一定会即时写到磁盘上。
      * 要保证这一点，需要调用force()方法。
      * force()方法有一个boolean类型的参数，指明是否同时将文件元数据（权限信息等）写到磁盘上。
      */
    channel.force(true)
    val elapsedTime = SystemTime.milliseconds - startTime
    LogFlushStats.recordFlushRequest(elapsedTime)
    if (logger.isDebugEnabled){
      logger.debug("flush time " + elapsedTime)
    }
    setHighWaterMark.set(sizeInBytes) // 设置高水位
    if (logger.isDebugEnabled) {
      logger.debug("flush high water mark:" + highWaterMark)
    }
  }

  /**
    * Close this message set
    */
  def close() = {
    if (mutable)
      flush()
    channel.close()
  }

  /**
    * Recover log up to the last complete entry. Truncate off any bytes from any incomplete messages written
    */
  def recover(): Long = {
    checkMutable()
    val len = channel.size
    val buffer = ByteBuffer.allocate(4)
    var validUpTo: Long = 0
    var next = 0L
    do {
      next = validateMessage(channel, validUpTo, len, buffer)
      if (next >= 0)
        validUpTo = next
    } while (next >= 0)
    channel.truncate(validUpTo)
    setSize.set(validUpTo)
    setHighWaterMark.set(validUpTo)
    if (logger.isDebugEnabled)
      logger.info("recover high water mark:" + highWaterMark)
    /* This should not be necessary, but fixes bug 6191269 on some OSs. */
    channel.position(validUpTo)
    needRecover.set(false)
    len - validUpTo
  }

  /**
    * Read, validate, and discard a single message, returning the next valid offset, and
    * the message being validated
    */
  private def validateMessage(channel: FileChannel, start: Long, len: Long, buffer: ByteBuffer): Long = {
    buffer.rewind()
    var read = channel.read(buffer, start)
    if (read < 4)
      return -1

    // check that we have sufficient bytes left in the file
    val size = buffer.getInt(0)
    if (size < Message.MinHeaderSize)
      return -1

    val next = start + 4 + size
    if (next > len)
      return -1

    // read the message
    val messageBuffer = ByteBuffer.allocate(size)
    var curr = start + 4
    while (messageBuffer.hasRemaining) {
      read = channel.read(messageBuffer, curr)
      if (read < 0)
        throw new IllegalStateException("File size changed during recovery!")
      else
        curr += read
    }
    messageBuffer.rewind()
    val message = new Message(messageBuffer)
    if (!message.isValid)
      return -1
    else
      next
  }

}

trait LogFlushStatsMBean {
  def getFlushesPerSecond: Double

  def getAvgFlushMs: Double

  def getTotalFlushMs: Long

  def getMaxFlushMs: Double

  def getNumFlushes: Long
}

@threadsafe
class LogFlushStats extends LogFlushStatsMBean {
  private val flushRequestStats = new SnapshotStats

  def recordFlushRequest(requestMs: Long) = flushRequestStats.recordRequestMetric(requestMs)

  def getFlushesPerSecond: Double = flushRequestStats.getRequestsPerSecond

  def getAvgFlushMs: Double = flushRequestStats.getAvgMetric

  def getTotalFlushMs: Long = flushRequestStats.getTotalMetric

  def getMaxFlushMs: Double = flushRequestStats.getMaxMetric

  def getNumFlushes: Long = flushRequestStats.getNumRequests
}

object LogFlushStats {
  private val logger = Logger.getLogger(getClass())
  private val LogFlushStatsMBeanName = "kafka:type=kafka.LogFlushStats"
  private val stats = new LogFlushStats
  Utils.swallow(logger.warn, Utils.registerMBean(stats, LogFlushStatsMBeanName))

  def recordFlushRequest(requestMs: Long) = stats.recordFlushRequest(requestMs)

  //  def test(): Unit ={
  //    println(123)
  //  }
  //  Utils.swallow(logger.warn,test())
}
