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

package kafka.api

import java.nio._
import kafka.network._
import kafka.utils._

object FetchRequest {
    
  def readFrom(buffer: ByteBuffer): FetchRequest = {
    val topic = Utils.readShortString(buffer, "UTF-8")
    val partition = buffer.getInt()
    val offset = buffer.getLong()
    val size = buffer.getInt()
    new FetchRequest(topic, partition, offset, size)
  }
}

class FetchRequest(val topic: String,
                   val partition: Int,
                   val offset: Long, 
                   val maxSize: Int) extends Request(RequestKeys.Fetch) {
  
  def writeTo(buffer: ByteBuffer) {
    Utils.writeShortString(buffer, topic, "UTF-8") // 放入topic
    buffer.putInt(partition)  // 放入partition，占用4个字节
    buffer.putLong(offset)  // 放入offset，占用 8个字节
    buffer.putInt(maxSize)   // 占用4个字节
  }
  
  def sizeInBytes(): Int ={
    val sizeInBytes = 2 + topic.length + 4 + 8 + 4
    println("-------------FetchRequest---------sizeInBytes=(2 + topic.length + 4 + 8 + 4)=:"+ sizeInBytes)
    sizeInBytes
  }

  override def toString(): String= "FetchRequest(topic:" + topic + ", part:" + partition +" offset:" + offset +
    " maxSize:" + maxSize + ")"
}
