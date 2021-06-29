package other.kafka

import java.util

object TestMySet {

  import scala.collection.mutable.Set // 可以在任何地方引入 可变集合

  def main(args: Array[String]): Unit = {

    import scala.collection.mutable.Set // 可以在任何地方引入 可变集合

    val mutableSet = Set(1, 2, 3)
    println(mutableSet.getClass.getName) // scala.collection.mutable.HashSet

    mutableSet.add(4)
    mutableSet.remove(1)
    mutableSet += 5
    mutableSet -= 2

    println(mutableSet) // Set(5, 3, 4)

    val another = mutableSet.toSet
    println(another.getClass.getName) // scala.collection.immutable.Set


    val set1 = Set("")
    set1 += ("test1")
    set1.add("test2")
    set1.add("test3")

    val set2 = Set("")
    set2.add("test1")
    set2.add("test2")
    set2.add("test5")

    var set = set1.diff(set2);
    println(set)

    //    交集：
    //    Set(1, 2, 3) & Set(2, 4) // &方法等同于interset方法
    //    Set(1, 2, 3) intersect Set(2, 4)
    //    并集：
    //    Set(1, 2, 3) ++ Set(2, 4)
    //    Set(1, 2, 3) | Set(2, 4) // |方法等同于union方法
    //    Set(1, 2, 3) union Set(2, 4)
    //    差集：
    //    Set(1, 2, 3) -- Set(2, 4) //得到 Set(1,3)
    //   println( Set(1, 2, 3) &~ Set(2, 4))
    //    Set(1, 2, 3) diff Set(2, 4)
    //    添加或删除元素，可以直接用+,-方法来操作，添加删除多个元素可以用元组来封装：

    //    Set(1, 2, 3) + (2, 4)
    //    Set(1, 2, 3) - (2, 4)
  }
}
