package other.kafka

object TestFoldLeft {

  def main(args: Array[String]): Unit = {
    val lst = List(20,30,60,90)
    //0为初始值，b表示返回结果对象（迭代值），a表示lst集合中的每个值
    val rs = lst.foldLeft(0)((b,a)=>{
      b + a
    })
    //    运行过程为：b=0+a，即0+20=20
    //    b=20+a，即20+30=50
    //    b=50+a，即50+60=110
    //    b=110+a，即110+90=200
    //    此处的a为循环取出集合中的值
    //    最终结果: rs=200
    println(rs)

    var size: Long = lst.foldLeft(4)((sum, _) => {
      println("SUM : " + sum)
      sum + 8
    })
    println(size)
   //   4 + 8 = 12
    //  12 + 8 = 20
    // 20 + 8 = 28
    // 28 + 8 = 36

    //改写成另外一种方式：
    var  sum = 4
    for(i <- 0 until  lst.size){
      sum +=8
    }
    println(" -----------SUM: " + sum)
  }
}
