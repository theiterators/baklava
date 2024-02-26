package pl.iterators.baklava.fetcher

import org.reflections.Reflections
import org.specs2.specification.core.Env
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}

import scala.jdk.CollectionConverters._

class Specs2Fetcher extends Fetcher {
  override def fetch(reflections: Reflections, mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    val docsSpecClass = classOf[Specs2RouteBaklavaSpec]
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

        val env: Env = Env()

        val fragments = spec.suite.fragments(env).fragmentsList(env.executionEnv)

        val descriptions = fragments
          .map(_.description.show)
          .filter(_ != "\n")
          .filter(_.nonEmpty)

        env.shutdown()

        spec.suite.shutdownSpec()

        EnrichedRouteRepresentation(routeRepresentation, descriptions)
      }
      .toList
      .sortBy(s => (s.routeRepresentation.path, s.routeRepresentation.method))
  }

}
