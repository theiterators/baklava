package pl.iterators.baklava.fetcher

import org.reflections.Reflections
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}

import scala.jdk.CollectionConverters._

class ScalatestFetcher extends Fetcher {
  override def fetch(reflections: Reflections, mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    val docsSpecClass = classOf[ScalatestRouteBaklavaSpec]
    reflections
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

        spec.suite.shutdownSpec()

        EnrichedRouteRepresentation(routeRepresentation, descriptions)
      }
      .toList
      .sortBy(s => (s.routeRepresentation.path, s.routeRepresentation.method))
  }

}
