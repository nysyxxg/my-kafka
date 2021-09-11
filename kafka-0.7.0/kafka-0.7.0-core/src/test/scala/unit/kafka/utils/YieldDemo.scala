package unit.kafka.utils

import scala.io.Source


object YieldDemo {
  lazy val files = (new java.io.File("D:\\my-kafka\\kafka-0.7.0\\data\\test")).listFiles
  //    输出指定目录下的所有文件
  /*for(file    <-    files)
          {
                    println(file)
          }*/

  def fileLines(file: java.io.File) = {
    Source.fromFile(file).getLines.toList
  }


  def main(args: Array[String]): Unit = {
    val lengths =
      for {
        //    获取以.txt结尾的文件
        file <- files if file.getName.endsWith(".txt")
        line <- fileLines(file)
        trimmedLine = line.trim
        if trimmedLine.matches(".*love.*")
      } yield line + "：合计" + trimmedLine.length + "个字。"
    lengths.foreach(println)
  }
}