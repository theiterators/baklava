package pl.iterators.baklava.akkahttp

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpMethods
import pl.iterators.baklava.core.model.RouteRepresentation
import pl.iterators.kebs.jsonschema.KebsJsonSchema
import pl.iterators.kebs.scalacheck.KebsScalacheckGenerators

trait LibraryAkkaHttpBaseRouteDocSpec
    extends KebsJsonSchema
    with KebsScalacheckGenerators
    with RequestBuilding {

  val routeRepresentation: RouteRepresentation[_, _]

  lazy val TestRequest = new RequestBuilder(
    HttpMethods.getForKeyCaseInsensitive(routeRepresentation.method).get)

  def shutdownSpec(): Unit
}
