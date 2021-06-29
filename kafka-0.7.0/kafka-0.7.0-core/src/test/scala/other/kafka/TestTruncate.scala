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

package kafka

import java.io._
import java.nio._

/* This code tests the correct function of java's FileChannel.truncate--some platforms don't work. */
object TestTruncate {

  def main(args: Array[String]): Unit = {
    val name = File.createTempFile("kafka", ".test")
    println(" 生成的文件路径: " + name)
    name.deleteOnExit()
    val file = new RandomAccessFile(name, "rw").getChannel()

    val buffer = ByteBuffer.allocate(12) // 申请缓冲区
    buffer.putInt(4).putInt(4).putInt(4)
    buffer.rewind()

    file.write(buffer)
    println("position prior to truncate: " + file.position)

    read(name.getAbsolutePath)

    /**
      * 举个例子，每个块由 4 个字节组成，每写完 1 个块，就在另一个文件中记录一个当前文件的最新位置。
      * 比如写了5个块，共 20 个字节，检查点记录了五个：4，8，12，16，20。这时候又写了 2 个字节，崩了。
      *
      * 为了继续写文件，需要根据检查点的 20，将文件从 20 处截断。
      *
      * java nio FileChannel 的 truncate 可以干这个事，RandomAccessFile 和 FileOutputStream 都可以获得当前 FileChannel。
      *
      */
    // 在文件最后 4 字节前截断文件
    file.truncate(4)  // 截断文件,
    println("position after truncate to 4: " + file.position)
    println("-------------截断文件之后---------------------")
    read(name.getAbsolutePath)

  }


  //读取 10 个字节
  def read(file:String): Unit = {
    println("读取文件10个字节数据：")
    try {
      val in = new FileInputStream(file)
      try {
        val a = new Array[Byte](10)
        System.out.print("整个文件有 " + in.available + " 个字节：")
        in.read(a)
        var i = 0
        while (i < a.length) {
          System.out.print(a(i))
            i += 1; i - 1
        }
        System.out.println()
      } catch {
        case e: Exception =>
          e.printStackTrace()
      } finally {
        if (in != null) in.close()
      }
    }
  }
}
