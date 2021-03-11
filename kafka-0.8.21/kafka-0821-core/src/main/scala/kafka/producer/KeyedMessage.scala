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

package kafka.producer

/**
 * A topic, key, and value.
 * If a partition key is provided it will override the key for 
 * the purpose of partitioning but will not be stored.
 * 
 * 名称解释：partKey:分区关键字，当客户端应用程序实现Partitioner接口时，传入参数key为分区关键字，
 * 根据key和numPartitions，返回分区(partitions)索引。记住partitions分区索引是从0开始的。
 * 
 */
case class KeyedMessage[K, V](val topic: String, val key: K, val partKey: Any, val message: V) {
  if(topic == null)
    throw new IllegalArgumentException("Topic cannot be null.")
  
  // topic --- value
  def this(topic: String, message: V) = this(topic, null.asInstanceOf[K], null, message)
  // topic --- key --- value
  def this(topic: String, key: K, message: V) = this(topic, key, key, message)
  
  def partitionKey = {
    if(partKey != null)
      partKey
    else if(hasKey)
      key
    else
      null  
  }
  
  def hasKey = key != null
}

/**
Scala class和case class的区别:
  1 、初始化的时候可以不用new，当然你也可以加上，普通类一定需要加new；
  2、toString的实现更漂亮；
　  3、默认实现了equals 和hashCode；
  4、默认是可以序列化的，也就是实现了Serializable ；
　  5、自动从scala.Product中继承一些函数;
　  6、case class构造函数的参数是public级别的，我们可以直接访问；
  7、支持模式匹配；
　　其实感觉case class最重要的特性应该就是支持模式匹配。这也是我们定义case class的唯一理由
* 
*/
