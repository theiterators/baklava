package pl.iterators.baklava.core

import org.reflections.Reflections
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.formatters.Formatter
import scala.collection.JavaConverters.asScalaSetConverter

object Generator {

  def generate(mainPackageName: String, outputDir: String, fetcherName: String, formatterName: String): Unit = {
    val fetcher   = dynamicallyLoad(fetcherName, classOf[Fetcher])
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
      .getOrElse(sys.error(s"Unable to find class with name ${className} in classPath"))
  }
}
