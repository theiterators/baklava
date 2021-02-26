package pl.iterators.baklava.core

import pl.iterators.baklava.core.Generator.dynamicallyLoad
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.formatters.Formatter

object GenerateOutputFromRouteDocSpec {

  private lazy val fetchers = List[String](
    "AkkaHttpSpecs2Fetcher",
    "AkkaHttpScalatestFetcher"
  )

  private lazy val formatters = List[String](
    "SimpleOutputFormatter",
    "OpenApiFormatter",
    "TsFormatter",
    "TsStrictFormatter"
  )

  def main(args: Array[String]): Unit = {
    if (args.length != 4) {
      sys.error("Incorrect execution. It should be called with params [packageName] [outputDir] [fetcher] [formatter]")
    }

    val mainPackageName = args(0)
    val outputDir       = args(1)
    val fetcherName = fetchers
      .find(_ == args(2))
      .getOrElse(sys.error(s"Unable to get fetcher with given name. Available names: ${fetchers
        .mkString("[", ", ", "]")}"))
    val formatterName = formatters
      .find(_ == args(3))
      .getOrElse(sys.error(s"Unable to get formatter with given name. Available names: ${formatters
        .mkString("[", ", ", "]")}"))

    Generator.generate(mainPackageName, outputDir, fetcherName, formatterName)
    println("Generated test spec successfully.")
  }
}
