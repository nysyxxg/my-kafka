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

package kafka.consumer

import kafka.message.ByteBufferMessageSet

/**
 * ConsumerIterator的实现可能会造成数据的重复发送（这要看生产者如何生产数据），
 * FetchedDataChunk是一个数据集合，它内部会包含很多数据块，一个数据块可能包含多条消息，
        但同一个数据块中的消息只有一个offset，所以当一个消息块有多条数据，处理完部分数据发生异常时，
        消费者重新去取数据，就会再次取得这个数据块，然后消费过的数据就会被重新消费。
        这篇文章转载自田加国：http://www.tianjiaguo.com/system-architecture/kafka/kafka的zookeeperconsumer实现/
 */
case class FetchedDataChunk(messages: ByteBufferMessageSet,
                            topicInfo: PartitionTopicInfo,
                            fetchOffset: Long)
