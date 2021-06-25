package other

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

object StreamTest {

  def main(args: Array[String]): Unit = {

    val list = List(1, 2, 3)
    val newList = Stream.continually(list.toStream).flatten.take(10).toList
    //    newList.foreach(println)

    val queue = new LinkedBlockingQueue[String](10)
    queue.offer("1")
    queue.offer("2")
    queue.offer("3")
    queue.offer("4")
    queue.offer("5")
    var lastSend = System.currentTimeMillis
    val queueTime = 1000
    //  从队列queue 不断的获取数据，如果不为null就进行 foreach 操作
    Stream.continually(queue.poll(scala.math.max(0, (lastSend + queueTime) - System.currentTimeMillis), TimeUnit.MILLISECONDS))
      .takeWhile(item => if (item != null) true else false).foreach { item => {
      println("---------------->" + item)
    }
    }
  }
}
