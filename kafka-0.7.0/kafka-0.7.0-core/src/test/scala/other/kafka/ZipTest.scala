package other.kafka

object ZipTest {
    def main(args: Array[String]): Unit = {
      val names = Array("darren", "anne")
      val ages = Array(18, 17)
      val cards = Array(100)

      val zipResult = names.zip(ages)
      zipResult.foreach(println(_))

      zipResult.foreach(item => {
        println("name: " + item._1 + " age: " + item._2)
      })

      names.zip(cards).foreach(println(_))

      names.zipWithIndex.foreach(println(_))
    }

  }
