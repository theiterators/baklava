package pl.iterators.baklava.core.fetchers

import org.reflections.Reflections
import pl.iterators.baklava.core.model.EnrichedRouteRepresentation

trait Fetcher {
  def fetch(reflections: Reflections, mainPackageName: String): List[EnrichedRouteRepresentation[_, _]]
}
