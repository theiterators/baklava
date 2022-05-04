package pl.iterators.baklava.generator

import org.reflections.Reflections
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.formatter.Formatter

import scala.collection.JavaConverters.asScalaSetConverter

object Generator {

  def generate(mainPackageName: String, outputDir: String, fetcherName: String, formatterNames: Seq[String]): Unit = {
    val reflections = new Reflections(mainPackageName, "pl.iterators.baklava")

    val fetcher              = dynamicallyLoad(reflections, fetcherName, classOf[Fetcher])
    val routeRepresentations = fetcher.fetch(reflections, mainPackageName)

    formatterNames.foreach { formatterName =>
      val formatter = dynamicallyLoad(reflections, formatterName, classOf[Formatter])
      formatter.generate(outputDir, routeRepresentations)
    }
  }

  private def dynamicallyLoad[T](reflections: Reflections, className: String, classOf: Class[T]): T = {
    reflections
      .getSubTypesOf(classOf)
      .asScala
      .find(_.getSimpleName == className)
      .map { specClazz =>
        specClazz.getConstructor().newInstance()
      }
      .getOrElse(sys.error(s"Unable to find class with name $className in classPath"))
  }
}
