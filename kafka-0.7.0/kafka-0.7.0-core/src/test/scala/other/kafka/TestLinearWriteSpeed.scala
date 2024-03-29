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

package kafka

import java.io._
import java.nio._
import java.nio.channels._
import joptsimple._

/**
  * 测试线性写入速度
  */
object TestLinearWriteSpeed {

  def main(args: Array[String]): Unit = {
    //    val parser = new OptionParser
    //    val bytesOpt = parser.accepts("bytes", "REQUIRED: The number of bytes to write.")
    //                           .withRequiredArg
    //                           .describedAs("num_bytes")
    //                           .ofType(classOf[java.lang.Integer])
    //    val sizeOpt = parser.accepts("size", "REQUIRED: The size of each write.")
    //                           .withRequiredArg
    //                           .describedAs("num_bytes")
    //                           .ofType(classOf[java.lang.Integer])
    //    val filesOpt = parser.accepts("files", "REQUIRED: The number of files.")
    //                           .withRequiredArg
    //                           .describedAs("num_files")
    //                           .ofType(classOf[java.lang.Integer])
    //                           .defaultsTo(1)
    //
    //    val options = parser.parse(args : _*)
    //
    //    for(arg <- List(bytesOpt, sizeOpt, filesOpt)) {
    //      if(!options.has(arg)) {
    //        System.err.println("Missing required argument \"" + arg + "\"")
    //        parser.printHelpOn(System.err)
    //        System.exit(1)
    //      }
    //    }

    //    val bytesToWrite = options.valueOf(bytesOpt).intValue
    //    val bufferSize = options.valueOf(sizeOpt).intValue
    //    val numFiles = options.valueOf(filesOpt).intValue

    val bytesToWrite = 2000 * 1024 * 1024  // 写入数据的总大小
    val bufferSize = 1024
    val numFiles = 10

    val buffer = ByteBuffer.allocate(bufferSize)
    while (buffer.hasRemaining) {
      buffer.put(123.asInstanceOf[Byte])
    }

    val files = new Array[File](numFiles)
    val channels = new Array[FileChannel](numFiles)
    for (i <- 0 until numFiles) {
      val file = File.createTempFile("kafka-test", ".dat")
      println("文件路径：" + file)
      file.deleteOnExit()
      files(i) = file
      channels(i) = new RandomAccessFile(file, "rw").getChannel()
    }

    val begin = System.currentTimeMillis
    for (i <- 0 until bytesToWrite / bufferSize) {
      buffer.rewind()
      channels(i % numFiles).write(buffer)
    }
    val ellapsedSecs = (System.currentTimeMillis - begin) / 1000.0
    System.out.println(bytesToWrite / (1024 * 1024 * ellapsedSecs) + " MB per sec")

    Thread.sleep(3)
    // 删除文件
    for (i <- 0 until numFiles) {
      println("删除文件： " + files(i))
      files(i).delete()
    }
  }

}
