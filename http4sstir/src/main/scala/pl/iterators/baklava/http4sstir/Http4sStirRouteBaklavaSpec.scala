package pl.iterators.baklava.http4sstir

import org.http4s.Method
import pl.iterators.baklava.core.fetchers.RouteBaklavaSpec
import pl.iterators.stir.testkit.RequestBuilding

trait Http4sStirRouteBaklavaSpec extends RouteBaklavaSpec with RequestBuilding {

  lazy val TestRequest = new RequestBuilder(Method.fromString(routeRepresentation.method).toOption.get)
}
