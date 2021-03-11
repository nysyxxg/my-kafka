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
package kafka.producer.async

import kafka.utils.VerifiableProperties

/**
 * request.required.acks  默认值： 0 
 * 0： 表示producer无需等待leader的确认，
 * 1: 代表需要leader确认写入它的本地log并立即确认，
 * -1:代表所有的备份都完成后确认。 仅仅for sync  【对异步发送有效】
 * 
 * request.timeout.ms 10000 确认超时时间
 */
trait AsyncProducerConfig {
  val props: VerifiableProperties

  /* maximum time, in milliseconds, for buffering data on the producer queue 
   * 默认值： 5000  
   * 在producer queue的缓存的数据最大时间，仅仅for asyc  【对异步发送有效】
   * */
  val queueBufferingMaxMs = props.getInt("queue.buffering.max.ms", 5000)

  /** the maximum size of the blocking queue for buffering on the producer 
   *  producer 缓存的消息的最大数量，仅仅for asyc 【对异步发送有效】
   **/
  val queueBufferingMaxMessages = props.getInt("queue.buffering.max.messages", 10000)

  /**
   * Timeout for event enqueue:
   * 0: events will be enqueued immediately or dropped if the queue is full
   * -ve: enqueue will block indefinitely if the queue is full
   * +ve: enqueue will block up to this many milliseconds if the queue is full
   * 0： 当queue满时丢掉，
   * 负值是queue满时block 【block 就是阻塞的意思】,
   * 正值是queue满时block【阻塞】相应的时间，仅仅for asyc
   */
  val queueEnqueueTimeoutMs = props.getInt("queue.enqueue.timeout.ms", -1)

  /** the number of messages batched at the producer 
   *  一批消息的数量，仅仅for asyc，默认值： 200
   *  */
  val batchNumMessages = props.getInt("batch.num.messages", 200)

  /** the serializer class for values */
  val serializerClass = props.getString("serializer.class", "kafka.serializer.DefaultEncoder")
  
  /** the serializer class for keys (defaults to the same as for values) */
  val keySerializerClass = props.getString("key.serializer.class", serializerClass)
  
}
