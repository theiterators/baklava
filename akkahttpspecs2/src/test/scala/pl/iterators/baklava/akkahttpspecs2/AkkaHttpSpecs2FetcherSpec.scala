package pl.iterators.baklava.akkahttpspecs2

import org.specs2.mutable.Specification
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}
import pl.iterators.baklava.sprayjson.SprayJsonStringProvider
import pl.iterators.kebs.jsonschema.{KebsJsonSchema, KebsJsonSchemaPredefs}
import pl.iterators.kebs.scalacheck.{KebsArbitraryPredefs, KebsScalacheckGenerators}
import spray.json.DefaultJsonProtocol

trait LibraryTestSpec extends LibraryAkkaHttpSpecs2RouteDocSpec {
  override def shutdownSpec(): Unit = ()
}

object TestData
    extends KebsArbitraryPredefs
    with KebsJsonSchemaPredefs
    with DefaultJsonProtocol
    with SprayJsonStringProvider
    with KebsJsonSchema
    with KebsScalacheckGenerators {
  val routeRepresentation1: RouteRepresentation[Unit, Unit] = RouteRepresentation(
    "description1",
    "method1",
    "path1"
  )

  val routeRepresentation2: RouteRepresentation[Unit, Unit] = RouteRepresentation(
    "description1",
    "method1",
    "path1"
  )

  val text11 = "returns Ok test case"
  val text12 = "returns NotFound test"

  val text21 = "inaccessible test"
}

class InnerTest1 extends LibraryTestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = TestData.routeRepresentation1

  "it" should {
    TestData.text11 in {
      1 shouldEqual 1
    }

    TestData.text12 in {
      1 shouldEqual 1
    }
  }
}

class InnerTest2 extends LibraryTestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = TestData.routeRepresentation2

  TestData.text21 in {
    1 shouldEqual 1
  }

}

class AkkaHttpSpecs2FetcherSpec extends Specification {

  "should fetch all class that extends LibraryAkkaHttpScalatestRouteDocSpec" in {
    val fetcher = new AkkaHttpSpecs2Fetcher

    fetcher.fetch("pl.iterators.baklava.akkahttpspecs2") should containTheSameElementsAs(
      List(
        EnrichedRouteRepresentation(TestData.routeRepresentation1, List("it should", TestData.text11, TestData.text12)),
        EnrichedRouteRepresentation(TestData.routeRepresentation2, List(TestData.text21)),
      ))
  }

}
