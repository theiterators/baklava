package pl.iterators.baklava.core.fetchers

import pl.iterators.baklava.core.model.RouteRepresentation
import pl.iterators.kebs.jsonschema.KebsJsonSchema
import pl.iterators.kebs.scalacheck.KebsScalacheckGenerators

trait RouteBaklavaSpec extends KebsJsonSchema with KebsScalacheckGenerators {

  val routeRepresentation: RouteRepresentation[_, _]

  def shutdownSpec(): Unit
}
