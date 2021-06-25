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

package kafka.api

import java.nio._
import kafka.message._
import kafka.network._
import kafka.utils._

object ProducerRequest {
  val RandomPartition = -1

  def readFrom(buffer: ByteBuffer): ProducerRequest = {
    println("-------------ProducerRequest-------------------readFrom-------------------------")
    val topic = Utils.readShortString(buffer, "UTF-8") // 从buffer中获取topic
    val partition = buffer.getInt  // 从buffer中获取分区
    val messageSetSize = buffer.getInt  // 获取消息集合的大小
    val messageSetBuffer = buffer.slice()// 此方法返回新的字节缓冲区
    // 新的messageSetBuffer，还有messageSetSize 这个大小的数据需要读取出来
    messageSetBuffer.limit(messageSetSize) // limit：指定还有多少数据需要取出(在从缓冲区写入通道时)，或者还有多少空间可以放入数据(在从通道读入缓冲区时)。
    buffer.position(buffer.position + messageSetSize)
    println("-------------ProducerRequest-------------------readFrom-----------------------messageSetSize--"+ messageSetSize)
    println("-------------ProducerRequest-------------------readFrom-----------------------messageSetBuffer--"+ messageSetBuffer)
    new ProducerRequest(topic, partition, new ByteBufferMessageSet(messageSetBuffer)) // 将需要写入的消息，都封装为ByteBufferMessageSet
  }
}

class ProducerRequest(val topic: String,
                      val partition: Int,
                      val messages: ByteBufferMessageSet) extends Request(RequestKeys.Produce) {
    println("-------------ProducerRequest--------------------init 初始化ProducerRequest-------------------------")

  override def writeTo(buffer: ByteBuffer) {
    println("-------------ProducerRequest----------------------writeTo-------------buffer-对象-" + buffer)
    Utils.writeShortString(buffer, topic, "UTF-8") // 将topic的信息放入buffer
    buffer.putInt(partition)  // 将 partition的值放入 buffer
    buffer.putInt(messages.serialized.limit) // 将消息的大小放入buffer
    buffer.put(messages.serialized)
    println("-------------ProducerRequest----------------------writeTo-------------------------messages.serialized.limit-" +  messages.serialized.limit)
    println("-------------ProducerRequest----------------------writeTo-------------------------messages.serialized-" + new String(messages.serialized.array()))
    messages.serialized.rewind // rewind()在读写模式下都可用，它单纯的将当前位置置0，同时取消mark标记，仅此而已；
    // 也就是说写模式下limit仍保持与Buffer容量相同，只是重头写而已；
  }

  override def sizeInBytes(): Int ={
    println("-------------ProducerRequest-------------------------sizeInBytes-------------messages.sizeInBytes =" + messages.sizeInBytes.asInstanceOf[Int])
    val dataSize = 2 + topic.length + 4 + 4 + messages.sizeInBytes.asInstanceOf[Int]
    println("-------------ProducerRequest-------------------------sizeInBytes--------- 2 + topic.length + 4 + 4=" + ( 2 + topic.length + 4 + 4))
    println("-------------ProducerRequest-------------------------sizeInBytes--------------------------dataSize=" + dataSize)
    dataSize
  }

  def getTranslatedPartition(randomSelector: String => Int): Int = {
    if (partition == ProducerRequest.RandomPartition)
      return randomSelector(topic)
    else
      return partition
  }

  override def toString: String = {
    val builder = new StringBuilder()
    builder.append("ProducerRequest(")
    builder.append(topic + ",")
    builder.append(partition + ",")
    builder.append(messages.sizeInBytes)
    builder.append(")")
    builder.toString
  }

  override def equals(other: Any): Boolean = {
    other match {
      case that: ProducerRequest =>
        (that canEqual this) && topic == that.topic && partition == that.partition &&
          messages.equals(that.messages)
      case _ => false
    }
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ProducerRequest]

  override def hashCode: Int = 31 + (17 * partition) + topic.hashCode + messages.hashCode

}
