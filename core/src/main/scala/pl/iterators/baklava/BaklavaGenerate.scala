package pl.iterators.baklava

object BaklavaGenerate {

  def main(args: Array[String]): Unit = {
    val configMap = args.map { entry =>
      val Array(key, value) = entry.split("=", 2)
      key -> value
    }.toMap
    println(s"Executing baklava generate")
    BaklavaDslFormatter.formatters.foreach(_.mergeChunks(configMap))
  }
}
