package pl.iterators.baklava

object BaklavaGenerate {

  def main(args: Array[String]): Unit = {
    println("Executing baklava generate")
    BaklavaDslFormatter.formatters.foreach(_.mergeChunks())
  }
}
