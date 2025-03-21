package pl.iterators.baklava

import java.util.Base64

object BaklavaGenerate {
  def main(args: Array[String]): Unit = {
    val configMap = args.map { entry =>
      val splitIndex = entry.indexOf('|')
      if (splitIndex >= 0) {
        val key          = entry.substring(0, splitIndex)
        val encodedValue = entry.substring(splitIndex + 1)
        val value        = new String(Base64.getDecoder.decode(encodedValue), "UTF-8")
        key -> value
      } else {
        entry -> ""
      }
    }.toMap
    val calls = BaklavaSerialize.listSerializedCalls()
    BaklavaDslFormatter.formatters.foreach(_.create(configMap, calls))
    BaklavaSerialize.cleanSerializedCalls()
  }
}
