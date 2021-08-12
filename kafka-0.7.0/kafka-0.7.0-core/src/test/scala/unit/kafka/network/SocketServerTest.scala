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

;

import java.net._
import java.io._
import java.nio._
import java.nio.channels._
import org.junit._
import junit.framework.Assert._
import org.scalatest.junit.JUnitSuite
import kafka.utils.TestUtils
import kafka.network._
import java.util.Random
import org.apache.log4j._

class SocketServerTest extends JUnitSuite {

  Logger.getLogger("kafka").setLevel(Level.INFO)

  def echo(receive: Receive): Option[Send] = {
    val id = receive.buffer.getShort
    Some(new BoundedByteBufferSend(receive.buffer.slice))  // slice 分隔buffer数据
  }

  val server = new SocketServer(port = TestUtils.choosePort,
    numProcessorThreads = 1,
    monitoringPeriodSecs = 30,
    handlerFactory = (requestId: Short, receive: Receive) => echo,
    maxRequestSize = 50)
  server.startup()

  // 测试发送一条数据请求
  def sendRequest(id: Short, request: Array[Byte]): Array[Byte] = {
    val socket = new Socket("localhost", server.port)
    val outgoing = new DataOutputStream(socket.getOutputStream)
    outgoing.writeInt(request.length + 2) // 写入数据长度+2
    outgoing.writeShort(id) // short  2字节 ---写入请求id
    outgoing.write(request) //写入请求的数据
    outgoing.flush()

    val incoming = new DataInputStream(socket.getInputStream)
    val len = incoming.readInt()
    println("response -----------------------------------------------------len: " + len)

    val response = new Array[Byte](len)
    incoming.readFully(response)

    println("response -----------------------------------------------------data: " + new String(response))
    socket.close()
    response
  }

  @After
  def cleanup() {
    server.shutdown()
  }

  @Test
  def simpleRequest() {
    val response = new String(sendRequest(0, "hello".getBytes))

  }

  @Test(expected = classOf[IOException])
  def tooBigRequestIsRejected() {
    val tooManyBytes = new Array[Byte](server.maxRequestSize + 1)
    new Random().nextBytes(tooManyBytes)
    sendRequest(0, tooManyBytes)
  }

}
