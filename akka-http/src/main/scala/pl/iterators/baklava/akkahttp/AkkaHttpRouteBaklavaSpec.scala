package pl.iterators.baklava.akkahttp

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpMethods
import pl.iterators.baklava.core.fetchers.RouteBaklavaSpec

trait AkkaHttpRouteBaklavaSpec extends RouteBaklavaSpec with RequestBuilding {

  lazy val TestRequest = new RequestBuilder(HttpMethods.getForKeyCaseInsensitive(routeRepresentation.method).get)
}
