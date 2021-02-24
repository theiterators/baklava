package pl.iterators.baklava.akkahttpspecs2

import org.reflections.Reflections
import org.specs2.specification.core.Env
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.model.{
  EnrichedRouteRepresentation,
  RouteRepresentation
}

import scala.collection.JavaConverters.asScalaSetConverter

class AkkaHttpSpecs2Fetcher extends Fetcher {
  override def fetch(
      mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    val docsSpecClass = classOf[LibraryAkkaHttpSpecs2RouteDocSpec]
    new Reflections(mainPackageName)
      .getSubTypesOf(docsSpecClass)
      .asScala
      .filterNot(_.isInterface)
      .map { specClazz =>
        val spec = specClazz.getConstructor().newInstance()
        val field = specClazz.getDeclaredField("routeRepresentation")
        field.setAccessible(true)
        val routeRepresentation =
          field.get(spec).asInstanceOf[RouteRepresentation[_, _]]

        val env: Env = Env()

        val fragments = spec.fragments(env).fragmentsList(env.executionEnv)

        val descriptions = fragments
          .zip(fragments.tail)
          .map(_._2.description.show)
          .filter(_ != "\n")
          .filter(_.nonEmpty)
          .tail
          .map(s => s"should $s")

        env.shutdown()

        spec.shutdownSpec()

        EnrichedRouteRepresentation(routeRepresentation, descriptions)
      }
      .toList
      .sortBy(s => (s.routeRepresentation.path, s.routeRepresentation.method))
  }

}
