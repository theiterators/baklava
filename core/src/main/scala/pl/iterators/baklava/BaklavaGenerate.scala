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
        if (generate(configMap, calls, BaklavaDslFormatter.formatters)) {
          BaklavaSerialize.cleanSerializedCalls() match {
            case Failure(exception) =>
              System.err.println(s"Failed to clean serialized calls: $exception")
            case Success(_) => // Success, no action needed
          }
        }
      case Failure(exception) =>
        System.err.println(s"Failed to list serialized calls: $exception")
    }
  }

  /** Runs the formatters over the captured calls, then once more per configured view (see [[BaklavaView]]) with that view's subset of the
    * calls under `target/baklava/views/<name>`. With zero captured calls generation is skipped (returning false), so a previously
    * generated spec is never overwritten by an empty one — zero calls almost always means the test suite didn't actually run, e.g. sbt 2's
    * incremental `test` restoring a cached result (see issue #135). A view that selects zero calls is skipped the same way. An invalid
    * `views` configuration fails generation before any formatter runs.
    */
  private[baklava] def generate(
      configMap: Map[String, String],
      calls: Seq[BaklavaSerializableCall],
      formatters: => Seq[BaklavaDslFormatter]
  ): Boolean = {
    if (calls.isEmpty) {
      System.err.println(
        "Baklava captured 0 calls — skipping generation so existing output is not overwritten. " +
          "If your tests did run, make sure they use the Baklava DSL. " +
          "If no tests ran, you may have hit sbt 2's incremental, cached `test` task; run `testFull` for a full run."
      )
      false
    } else {
      val views = BaklavaView.fromConfig(configMap) match {
        case Right(parsed) => parsed
        case Left(error)   => sys.error(s"Baklava: invalid '${BaklavaView.ConfigKey}' configuration: $error")
      }
      formatters.foreach(_.create(configMap, calls))
      views.foreach { view =>
        val selected = view.select(calls)
        if (selected.isEmpty) {
          System.err.println(
            s"Baklava: view '${view.name}' selected 0 of ${calls.size} captured calls — skipping its output so nothing empty is written."
          )
        } else {
          formatters.foreach(_.create(configMap, selected, view.outputRoot(BaklavaDslFormatter.DefaultOutputRoot)))
        }
      }
      true
    }
  }
}
