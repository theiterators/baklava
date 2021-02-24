package pl.iterators.baklava.core.fetchers

import pl.iterators.baklava.core.model.EnrichedRouteRepresentation

trait Fetcher {
  def fetch(mainPackageName: String): List[EnrichedRouteRepresentation[_, _]]
}
