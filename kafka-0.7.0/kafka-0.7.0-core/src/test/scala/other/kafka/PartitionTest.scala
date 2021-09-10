package other.kafka

object PartitionTest {

  def main(args: Array[String]): Unit = {
    val numbers = Seq(3, 7, 2, 9, 6, 5, 1, 4, 2)
    //(List(2, 6, 4, 2), List(3, 7, 9, 5, 1))
    var list = numbers partition (n => n % 2 == 0)
    println(list)

    var list2: (Seq[Int], Seq[Int]) = numbers.partition(n => n % 2 == 0) //根据计算函数，对集合进行分区
    println(list2._1)
    println(list2._2)

    println("------------------------partition 根据条件分隔两个分区------------------------------")
    var list3: (Seq[Int], Seq[Int]) = numbers partition (n => n > 5) //根据计算函数，对集合进行分区
    println("满足条件的分区： " + list3._1)
    println("不满足条件的分区： " + list3._2)
  }

}
