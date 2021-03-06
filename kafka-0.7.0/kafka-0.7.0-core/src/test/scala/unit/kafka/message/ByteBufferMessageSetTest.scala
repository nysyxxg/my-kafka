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

package kafka.message

import java.nio._
import junit.framework.Assert._
import org.junit.Test
import kafka.utils.TestUtils
import kafka.common.InvalidMessageSizeException

class ByteBufferMessageSetTest extends BaseMessageSetTestCases {

  override def createMessageSet(messages: Seq[Message]): ByteBufferMessageSet = 
    new ByteBufferMessageSet(NoCompressionCodec, messages: _*)
  
  @Test
  def testSmallFetchSize() {
    // create a ByteBufferMessageSet that doesn't contain a full message
    // iterating it should get an InvalidMessageSizeException
    val messages = new ByteBufferMessageSet(NoCompressionCodec, new Message("01234567890123456789".getBytes()))
    val buffer = messages.serialized.slice
    buffer.limit(10)
    val messageSetWithNoFullMessage = new ByteBufferMessageSet(buffer = buffer, initialOffset = 1000)
    try {
      for (message <- messageSetWithNoFullMessage)
        fail("shouldn't see any message")
    }
    catch {
      case e: InvalidMessageSizeException => //this is expected
      case e2 => fail("shouldn't see any other exceptions")
    }
  }

  @Test
  def testValidBytes() {
    {
      val messages = new ByteBufferMessageSet(NoCompressionCodec, new Message("hello".getBytes()), new Message("there".getBytes()))
      val buffer = ByteBuffer.allocate(messages.sizeInBytes.toInt + 2)
      buffer.put(messages.serialized)
      buffer.putShort(4)
      val messagesPlus = new ByteBufferMessageSet(buffer)
      assertEquals("Adding invalid bytes shouldn't change byte count", messages.validBytes, messagesPlus.validBytes)
    }

    // test valid bytes on empty ByteBufferMessageSet
    {
      assertEquals("Valid bytes on an empty ByteBufferMessageSet should return 0", 0,
        MessageSet.Empty.asInstanceOf[ByteBufferMessageSet].validBytes)
    }
  }

  @Test
  def testEquals() {
    var messages = new ByteBufferMessageSet(DefaultCompressionCodec, new Message("hello".getBytes()), new Message("there".getBytes()))
    var moreMessages = new ByteBufferMessageSet(DefaultCompressionCodec, new Message("hello".getBytes()), new Message("there".getBytes()))

    assertTrue(messages.equals(moreMessages))

    messages = new ByteBufferMessageSet(NoCompressionCodec, new Message("hello".getBytes()), new Message("there".getBytes()))
    moreMessages = new ByteBufferMessageSet(NoCompressionCodec, new Message("hello".getBytes()), new Message("there".getBytes()))

    assertTrue(messages.equals(moreMessages))
  }
  

  @Test
  def testIterator() {
    val messageList = List(
        new Message("msg1".getBytes),
        new Message("msg2".getBytes),
        new Message("msg3".getBytes)
      )

    // test for uncompressed regular messages
    {
      val messageSet = new ByteBufferMessageSet(NoCompressionCodec, messageList: _*)
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(messageSet.iterator))
      //make sure ByteBufferMessageSet is re-iterable.
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(messageSet.iterator))
      //make sure the last offset after iteration is correct
      assertEquals("offset of last message not expected", messageSet.last.offset, messageSet.serialized.limit)
    }

    // test for compressed regular messages
    {
      val messageSet = new ByteBufferMessageSet(DefaultCompressionCodec, messageList: _*)
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(messageSet.iterator))
      //make sure ByteBufferMessageSet is re-iterable.
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(messageSet.iterator))
      //make sure the last offset after iteration is correct
      assertEquals("offset of last message not expected", messageSet.last.offset, messageSet.serialized.limit)
    }

    // test for mixed empty and non-empty messagesets uncompressed
    {
      val emptyMessageList : List[Message] = Nil
      val emptyMessageSet = new ByteBufferMessageSet(NoCompressionCodec, emptyMessageList: _*)
      val regularMessgeSet = new ByteBufferMessageSet(NoCompressionCodec, messageList: _*)
      val buffer = ByteBuffer.allocate(emptyMessageSet.serialized.limit + regularMessgeSet.serialized.limit)
      buffer.put(emptyMessageSet.serialized)
      buffer.put(regularMessgeSet.serialized)
      buffer.rewind
      val mixedMessageSet = new ByteBufferMessageSet(buffer, 0, 0)
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(mixedMessageSet.iterator))
      //make sure ByteBufferMessageSet is re-iterable.
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(mixedMessageSet.iterator))
      //make sure the last offset after iteration is correct
      assertEquals("offset of last message not expected", mixedMessageSet.last.offset, mixedMessageSet.serialized.limit)
    }

    // test for mixed empty and non-empty messagesets compressed
    {
      val emptyMessageList : List[Message] = Nil
      val emptyMessageSet = new ByteBufferMessageSet(DefaultCompressionCodec, emptyMessageList: _*)
      val regularMessgeSet = new ByteBufferMessageSet(DefaultCompressionCodec, messageList: _*)
      val buffer = ByteBuffer.allocate(emptyMessageSet.serialized.limit + regularMessgeSet.serialized.limit)
      buffer.put(emptyMessageSet.serialized)
      buffer.put(regularMessgeSet.serialized)
      buffer.rewind
      val mixedMessageSet = new ByteBufferMessageSet(buffer, 0, 0)
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(mixedMessageSet.iterator))
      //make sure ByteBufferMessageSet is re-iterable.
      TestUtils.checkEquals[Message](messageList.iterator, TestUtils.getMessageIterator(mixedMessageSet.iterator))
      //make sure the last offset after iteration is correct
      assertEquals("offset of last message not expected", mixedMessageSet.last.offset, mixedMessageSet.serialized.limit)
    }
  }

}
