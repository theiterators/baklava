package pl.iterators.baklava.fetcher

import akka.http.scaladsl.testkit.Specs2RouteTest
import org.reflections.Reflections
import org.scalacheck.Gen
import org.specs2.mutable.{Specification, SpecificationLike}
import pl.iterators.baklava.core.fetchers.RouteBaklavaSpec
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}
import pl.iterators.baklava.sprayjson.SprayJsonStringProvider
import pl.iterators.kebs.jsonschema.{KebsJsonSchema, KebsJsonSchemaPredefs}
import pl.iterators.kebs.scalacheck.KebsArbitrarySupport
import spray.json.DefaultJsonProtocol

trait LibrarySpec2TestSpec extends Specs2RouteTest with SpecificationLike with RouteBaklavaSpec with Specs2RouteBaklavaSpec {
  override def shutdownSpec(): Unit = ()
}

object Specs2TestData
    extends KebsArbitrarySupport
    with KebsJsonSchemaPredefs
    with DefaultJsonProtocol
    with SprayJsonStringProvider
    with KebsJsonSchema {
  implicit val genParameters: Gen.Parameters = Gen.Parameters.default.withSize(5)

  val routeRepresentation1: RouteRepresentation[Unit, Unit] = RouteRepresentation("description1", "method1", "path1")

  val routeRepresentation2: RouteRepresentation[Unit, Unit] = RouteRepresentation("description1", "method1", "path1")

  val text11 = "returns Ok test case"
  val text12 = "returns NotFound test"

  val text21 = "inaccessible test"
}

class Specs2InnerTest1 extends LibrarySpec2TestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = Specs2TestData.routeRepresentation1

  "it" should {
    Specs2TestData.text11 in {
      1 shouldEqual 1
    }

    Specs2TestData.text12 in {
      1 shouldEqual 1
    }
  }
}

class Specs2InnerTest2 extends LibrarySpec2TestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = Specs2TestData.routeRepresentation2

  Specs2TestData.text21 in {
    1 shouldEqual 1
  }

}

class AkkaHttpSpecs2FetcherSpec extends Specification {

  "should fetch all class that extends Specs2RouteBaklavaSpec" in {
    val fetcher     = new Specs2Fetcher
    val reflections = new Reflections("pl.iterators.baklava")

    fetcher.fetch(reflections, "pl.iterators.baklava") should containTheSameElementsAs(
      List(
        EnrichedRouteRepresentation(Specs2TestData.routeRepresentation1, List("it should", Specs2TestData.text11, Specs2TestData.text12)),
        EnrichedRouteRepresentation(Specs2TestData.routeRepresentation2, List(Specs2TestData.text21))
      )
    )
  }

}
