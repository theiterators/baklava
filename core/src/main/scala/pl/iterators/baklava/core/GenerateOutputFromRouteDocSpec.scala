package pl.iterators.baklava.core

import org.reflections.Reflections
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.formatters.Formatter
import scala.collection.JavaConverters.asScalaSetConverter

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
      sys.error(
        "Incorrect execution. It should be called with params [packageName] [outputDir] [fetcher] [formatter]")
    }

    val mainPackageName = args(0)
    val outputDir = args(1)
    val fetcherName = fetchers
      .find(_ == args(2))
      .getOrElse(sys.error(
        s"Unable to get fetcher with given name. Available names: ${fetchers
          .mkString("[", ", ", "]")}"))
    val formatterName = formatters
      .find(_ == args(3))
      .getOrElse(sys.error(
        s"Unable to get formatter with given name. Available names: ${formatters
          .mkString("[", ", ", "]")}"))

    val fetcher = dynamicallyLoad(fetcherName, classOf[Fetcher])
    val formatter = dynamicallyLoad(formatterName, classOf[Formatter])

    val routeRepresentations = fetcher.fetch(mainPackageName)
    formatter.generate(outputDir, routeRepresentations)
    println("Generated test spec successfully.")
  }

  private def dynamicallyLoad[T](className: String, classOf: Class[T]): T = {
    new Reflections("pl.iterators.baklava")
      .getSubTypesOf(classOf)
      .asScala
      .find(_.getSimpleName == className)
      .map { specClazz =>
        specClazz.getConstructor().newInstance()
      }
      .getOrElse(
        sys.error(s"Unable to find class with name ${className} in classPath"))
  }
}
