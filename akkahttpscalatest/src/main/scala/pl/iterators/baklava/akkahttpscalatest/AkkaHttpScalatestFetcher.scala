package pl.iterators.baklava.akkahttpscalatest

import org.reflections.Reflections
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}

import scala.collection.JavaConverters.asScalaSetConverter

class AkkaHttpScalatestFetcher extends Fetcher {
  override def fetch(mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    val docsSpecClass = classOf[LibraryAkkaHttpScalatestRouteDocSpec]
    new Reflections(mainPackageName)
      .getSubTypesOf(docsSpecClass)
      .asScala
      .filterNot(_.isInterface)
      .map { specClazz =>
        val spec  = specClazz.getConstructor().newInstance()
        val field = specClazz.getDeclaredField("routeRepresentation")
        field.setAccessible(true)
        val routeRepresentation =
          field.get(spec).asInstanceOf[RouteRepresentation[_, _]]

        val descriptions = spec.suite.testNames.toList.map(_.stripPrefix(s"${routeRepresentation.method} ${routeRepresentation.path}"))

        spec.shutdownSpec()

        EnrichedRouteRepresentation(routeRepresentation, descriptions)
      }
      .toList
      .sortBy(s => (s.routeRepresentation.path, s.routeRepresentation.method))
  }

}
