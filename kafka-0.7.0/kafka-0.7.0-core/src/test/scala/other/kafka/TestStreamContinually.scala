package other.kafka

object TestStreamContinually {

  def main(args: Array[String]): Unit = {
      val l  = List(1, 2, 3)
//     val  list = Stream.continually(l.toStream).flatten.take(10).toList
    val  list = Stream.continually(1) .takeWhile(_ > 1).toList

    list.foreach(line=>{
      println(line)
    })
  }
}
