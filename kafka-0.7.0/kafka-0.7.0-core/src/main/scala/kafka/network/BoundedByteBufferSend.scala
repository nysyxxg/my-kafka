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

import java.nio._
import java.nio.channels._
import kafka.utils._

@nonthreadsafe
private[kafka] class BoundedByteBufferSend(val buffer: ByteBuffer) extends Send {
  println("-------------BoundedByteBufferSend----------init 初始化-------申请buffer--capacity:"+ buffer.capacity())
  private var sizeBuffer = ByteBuffer.allocate(4)
  sizeBuffer.putInt(buffer.limit) //  将数据的大小，放入缓冲区
  sizeBuffer.rewind()
  
  var complete: Boolean = false

  def this(size: Int) = this(ByteBuffer.allocate(size))  // 申请缓存区
  
  def this(request: Request) = { // 实例化对象
    this(request.sizeInBytes + 2) // 调用构造器，申请缓冲区，缓冲区大小为：封装发送消息的大小 + 2  =  2 + topic.length + 4 + 4 + messages.sizeInBytes.asInstanceOf[Int] + 2
    buffer.putShort(request.id) // 请求的id，放入buffer，占用2个字节
    request.writeTo(buffer)  // 调用 request 请求的writeTo，将 相关信息放入 buffer 写入到服务器
    buffer.rewind()
  }
  // 复写了父类的writeTo方法
 override def writeTo(channel: WritableByteChannel): Int = {
    println("-------------BoundedByteBufferSend-----------------复写了父类的writeTo方法------writeTo----------------------sizeBuffer----"+ buffer.limit())
    expectIncomplete()
    var written = 0
    // try to write the size if we haven't already
    if(sizeBuffer.hasRemaining){ // 当且仅当此缓冲区中至少剩余一个元素时，此方法才会返回true。
      written += channel.write(sizeBuffer) // 先写入数据Buffer的大小
    }
    // try to write the actual buffer itself
    if(!sizeBuffer.hasRemaining && buffer.hasRemaining){
      println("-------------BoundedByteBufferSend-----------------------writeTo-----------buffer-对象---"+ new String(buffer.array()) + " --capacity: " + buffer.capacity())
      println("-------------BoundedByteBufferSend-----------------------writeTo-------------capacity: " + buffer.capacity())
      written += channel.write(buffer) // 写入数据
    }
    // if we are done, mark it off
    if(!buffer.hasRemaining){
      complete = true
    }
   written
  }
    
}
