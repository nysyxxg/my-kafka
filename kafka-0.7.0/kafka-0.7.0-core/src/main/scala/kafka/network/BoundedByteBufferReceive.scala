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

import kafka.common.InvalidRequestException
import kafka.utils._

/**
  * Represents a communication between the client and server
  *
  */
@nonthreadsafe
private[kafka] class BoundedByteBufferReceive(val maxSize: Int) extends Receive {
  println("-------------BoundedByteBufferReceive-------------INIT 初始化BoundedByteBufferReceive-------------maxSize-" + maxSize)

  private val sizeBuffer: ByteBuffer = ByteBuffer.allocate(4)
  private var contentBuffer: ByteBuffer = null // 定义数据内容Buffer

  def this() = this(Int.MaxValue)

  var complete: Boolean = false

  /**
    * Get the content buffer for this transmission
    */
  override def buffer: ByteBuffer = {
    expectComplete()
    contentBuffer
  }

  /**
    * Read the bytes in this response from the given channel
    */
  override def readFrom(channel: ReadableByteChannel): Int = {
    println("-------------BoundedByteBufferReceive------------readFrom------start-从channel-读取数据：" + channel)
    expectIncomplete()
    var read = 0
    // have we read the request size yet?
    if (sizeBuffer.remaining > 0){
      read += Utils.read(channel, sizeBuffer)  //  先从channel 获取数据的大小
    }
    // have we allocated the request buffer yet?
    if (contentBuffer == null && !sizeBuffer.hasRemaining) {
      sizeBuffer.rewind()
      val size = sizeBuffer.getInt()  // 获取需要读取数据的大小
      if (size <= 0){
        throw new InvalidRequestException("%d is not a valid request size.".format(size))
      }
      if (size > maxSize) { // 如果读取的字节长度，超过maxSize ，就会抛出异常
        throw new InvalidRequestException("Request of length %d is not valid, it is larger than the maximum size of %d bytes.".format(size, maxSize))
      }
      contentBuffer = byteBufferAllocate(size) // 申请数据缓冲区
    }
    // if we have a buffer read some stuff into it3
    if (contentBuffer != null) {
      read = Utils.read(channel, contentBuffer) // 从channel 中读取数据
      // did we get everything?
      if (!contentBuffer.hasRemaining) {
        contentBuffer.rewind()
        complete = true
      }
    }
    println("-------------BoundedByteBufferReceive------------readFrom------end---从channel-读取数据：contentBuffer.capacity=" + contentBuffer.capacity())
    println("-------------BoundedByteBufferReceive------------readFrom------end---从channel-读取数据：complete=" + complete)
    read
  }
  // 申请内存缓冲区
  private def byteBufferAllocate(size: Int): ByteBuffer = {
    println("-------------BoundedByteBufferReceive------------byteBufferAllocate------申请缓冲区：size=" + size)
    var buffer: ByteBuffer = null
    try {
      buffer = ByteBuffer.allocate(size)
    }
    catch {
      case e: OutOfMemoryError =>
        throw new RuntimeException("OOME with size " + size, e)
      case e2 =>
        throw e2
    }
    buffer
  }
}
