package pl.iterators.baklava.pekkohttp

import org.apache.pekko.http.scaladsl.client.RequestBuilding
import org.apache.pekko.http.scaladsl.model.HttpMethods
import pl.iterators.baklava.core.fetchers.RouteBaklavaSpec

trait PekkoHttpRouteBaklavaSpec extends RouteBaklavaSpec with RequestBuilding {

  lazy val TestRequest = new RequestBuilder(HttpMethods.getForKeyCaseInsensitive(routeRepresentation.method).get)
}
