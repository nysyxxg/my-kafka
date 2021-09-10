package other.kafka

object TestStreamContinually {

  def main(args: Array[String]): Unit = {
      val list0  = List(3,0,1, 2, 3)
     val  list = Stream.continually(list0.toStream).flatten.take(10).takeWhile(_ >= 3).toList
//    val  list = Stream.continually(10) .takeWhile(item =>  item >  1).toList

    list.foreach(line=>{
      println(line)
    })
  }
}
