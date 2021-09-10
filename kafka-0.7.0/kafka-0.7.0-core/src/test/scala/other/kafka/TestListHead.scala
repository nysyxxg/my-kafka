package other.kafka

object TestListHead {

  def main(args: Array[String]): Unit = {

    val l1 = 1 to 10 toList

    println(l1.head);
    println(l1.tail);

    val  list = List();

    println(list.head);
    println(list.tail);
  }
}
