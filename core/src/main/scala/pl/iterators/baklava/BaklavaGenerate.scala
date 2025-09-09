package pl.iterators.baklava

import java.util.Base64
import scala.util.{Failure, Success}

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

    BaklavaSerialize.listSerializedCalls() match {
      case Success(calls) =>
        BaklavaDslFormatter.formatters.foreach(_.create(configMap, calls))
        BaklavaSerialize.cleanSerializedCalls() match {
          case Failure(exception) =>
            System.err.println(s"Failed to clean serialized calls: $exception")
          case Success(_) => // Success, no action needed
        }
      case Failure(exception) =>
        System.err.println(s"Failed to list serialized calls: $exception")
    }
  }
}
