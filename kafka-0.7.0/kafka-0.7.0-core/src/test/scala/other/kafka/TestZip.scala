package other.kafka

object TestZip {
  def main(args: Array[String]): Unit = {
    val year = List(2010, 2010, 2010, 2016, 2016)
    val month = List(1, 2, 3, 9, 10)

    val price = List(13.8, 32, 62.9, 66, 88, 99)

    val profit = List(1.1, 2.2, 3.3, 4.4, 5.5)

    val res1 = year zip month
    println(res1)
    res1.foreach(println)


    val res2 = price zip profit
    res2.foreach(println)


  }

}
