package pl.iterators.baklava.core

object GenerateOutputFromRouteDocSpec {

  private lazy val fetchers = List[String](
    "AkkaHttpSpecs2Fetcher",
    "AkkaHttpScalatestFetcher"
  )

  private lazy val formatters = List[String](
    "SimpleDocsFormatter",
    "OpenApiFormatter",
    "TsFormatter",
    "TsStrictFormatter"
  )

  def main(args: Array[String]): Unit = {
    if (args.length < 4) {
      sys.error("Incorrect execution. It should be called with params [packageName] [outputDir] [fetcher] [formatter...]")
    }

    val mainPackageName = args(0)
    val outputDir       = args(1)
    val fetcherName     = args(2)
    val formatterNames  = args.slice(3, args.length)
    val selectedFetcher = fetchers
      .find(_ == fetcherName)
      .getOrElse(sys.error(s"Unable to get fetcher with given name. Available names: ${fetchers
        .mkString("[", ", ", "]")}"))
    val selectedFormatters = formatterNames.map { formatterName =>
      formatters
        .find(_ == formatterName)
        .getOrElse(sys.error(s"Unable to get formatter with given name. Available names: ${formatters
          .mkString("[", ", ", "]")}"))
    }

    selectedFormatters.foreach { selectedFormatter =>
      Generator.generate(mainPackageName, outputDir, selectedFetcher, selectedFormatter)
    }
    println("Generated test spec successfully.")
  }
}
