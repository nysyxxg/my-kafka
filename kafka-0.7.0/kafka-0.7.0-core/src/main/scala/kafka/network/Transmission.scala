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

package kafka.network

import java.nio._
import java.nio.channels._
import org.apache.log4j.Logger

/**
  * Represents a stateful transfer of data to or from the network
  */
private[network] trait Transmission {

  protected val logger: Logger = Logger.getLogger(getClass())

  def complete: Boolean

  protected def expectIncomplete(): Unit = {
    if (complete)
      throw new IllegalStateException("This operation cannot be completed on a complete request.")
  }

  protected def expectComplete(): Unit = {
    if (!complete)
      throw new IllegalStateException("This operation cannot be completed on an incomplete request.")
  }

}

/**
  * A transmission that is being received from a channel
  */
private[kafka] trait Receive extends Transmission {

  def buffer: ByteBuffer

  def readFrom(channel: ReadableByteChannel): Int

  def readCompletely(channel: ReadableByteChannel): Int = {
    var read = 0
    while (!complete) {
      read = readFrom(channel)
      if (logger.isTraceEnabled)
        logger.trace(read + " bytes read.")
    }
    read
  }

}

/**
  * A transmission that is being sent out to the channel
  */
private[kafka] trait Send extends Transmission {
  // WritableByteChannel 是一个接口，NIO比较上层的接口
  def writeTo(channel: WritableByteChannel): Int

  def writeCompletely(channel: WritableByteChannel): Int = {
    println("-------------Send----------writeCompletely---------------writeTo--------------------------")
    var written = 0
    while (!complete) {
      written = writeTo(channel)  // 将数据写入到 channel，调用子类的实现方法
      if (logger.isTraceEnabled)
        logger.trace(written + " bytes written.")
    }
    written
  }

}

/**
  * A set of composite sends, sent one after another
  */
abstract class MultiSend[S <: Send](val sends: List[S]) extends Send {
  val expectedBytesToWrite: Int
  private var current = sends
  var totalWritten = 0

  override def writeTo(channel: WritableByteChannel): Int = {
    println("-------------MultiSend-------------------------writeTo--------------------------")
    expectIncomplete
    val written = current.head.writeTo(channel) //head 获取第一个元素,  有可能获取不到
    totalWritten += written
    if (current.head.complete)
      current = current.tail // tail 返回一个列表，包含除了第一元素之外的其他元素
    written
  }

  def complete: Boolean = {
    if (current == Nil) {
      if (totalWritten != expectedBytesToWrite)
        logger.error("mismatch in sending bytes over socket; expected: " + expectedBytesToWrite + " actual: " + totalWritten)
      return true
    }
    else
      return false
  }
}
