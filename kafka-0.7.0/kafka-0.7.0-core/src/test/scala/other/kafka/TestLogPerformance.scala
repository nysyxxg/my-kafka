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

import kafka.message._
import kafka.utils.{TestUtils, Utils}

/**
  * 测试日志写入性能
  */
object TestLogPerformance {

  def main(args: Array[String]): Unit = {
    val args = Array("50000","20","100","1")
    if(args.length < 4){
      Utils.croak("USAGE: java " + getClass().getName() + " num_messages message_size batch_size compression_codec")
    }
    val numMessages = args(0).toInt  // 多少条数据
    val messageSize = args(1).toInt // 每条消息多少字节
    val batchSize = args(2).toInt  // 每个批次多少条数
    val compressionCodec = CompressionCodec.getCompressionCodec(args(3).toInt) // 是否压缩
    val dir = TestUtils.tempDir()

    val log = new Log(dir, 50*1024*1024, 5000000, false)
    val bytes = new Array[Byte](messageSize)
    new java.util.Random().nextBytes(bytes)
    val message = new Message(bytes)
    val messages = new Array[Message](batchSize)
    for(i <- 0 until batchSize){
      messages(i) = message
    }

    val messageSet = new ByteBufferMessageSet(compressionCodec = compressionCodec, messages = messages: _*)
    val numBatches = numMessages / batchSize
    val start = System.currentTimeMillis()
    for(i <- 0 until numBatches){
      log.append(messageSet)
    }
    log.close()
    val ellapsed = (System.currentTimeMillis() - start) / 1000.0
    val writtenBytes = MessageSet.entrySize(message) * numMessages
    println("message size = " + MessageSet.entrySize(message))
    println("MB/sec: " + writtenBytes / ellapsed / (1024.0 * 1024.0))
    Utils.rm(dir)
  }
  
}
