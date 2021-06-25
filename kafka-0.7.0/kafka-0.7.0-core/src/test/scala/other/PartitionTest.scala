package other

object PartitionTest {

  def main(args: Array[String]): Unit = {
    val numbers = Seq(3, 7, 2, 9, 6, 5, 1, 4, 2)
    //(List(2, 6, 4, 2), List(3, 7, 9, 5, 1))
    numbers.partition(n => n % 2 == 0)

    /**
      * 对集合进行分组
      * 你是否尝试过将一个集合按一定的规则拆分成两个新的集合？比如，我们把某个集合拆分成偶数集和奇数集，partition 函数可以帮我们做到这一点：
      *
      */
   val  data: (Seq[Int], Seq[Int]) =  numbers  partition(n => n % 2 == 0)

    println("满足条件的数据： " + data._1)
    println("不满足： " + data._2)
  }
}
