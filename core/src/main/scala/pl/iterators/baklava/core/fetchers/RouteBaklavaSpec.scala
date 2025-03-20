package pl.iterators.baklava.core.fetchers

import pl.iterators.baklava.core.model.RouteRepresentation
import pl.iterators.kebs.jsonschema.KebsJsonSchema

trait RouteBaklavaSpec extends KebsJsonSchema {

  val routeRepresentation: RouteRepresentation[_, _]

  def shutdownSpec(): Unit
}
