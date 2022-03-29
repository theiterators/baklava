package pl.iterators.baklava.akkahttpscalatest

import org.scalatest.flatspec.{AnyFlatSpec, AnyFlatSpecLike}
import org.scalatest.matchers.should.Matchers
import org.specs2.mutable.Specification
import pl.iterators.baklava.circe.CirceJsonStringProvider
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.jsonschema.{KebsJsonSchema, KebsJsonSchemaPredefs}
import pl.iterators.kebs.scalacheck.{KebsArbitraryPredefs, KebsScalacheckGenerators}

trait LibraryTestSpec extends LibraryAkkaHttpScalatestRouteDocSpec with AnyFlatSpecLike with Matchers {
  override def shutdownSpec(): Unit = ()
}

object TestData
    extends KebsArbitraryPredefs
    with KebsJsonSchemaPredefs
    with KebsCirce
    with CirceJsonStringProvider
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

class InnerTest1 extends AnyFlatSpec with LibraryTestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = TestData.routeRepresentation1

  it should TestData.text11 in {
    1 shouldEqual 1
  }

  it should TestData.text12 in {
    1 shouldEqual 1
  }
}

class InnerTest2 extends AnyFlatSpec with LibraryTestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = TestData.routeRepresentation2

  it should TestData.text21 in {
    1 shouldEqual 1
  }

}

class AkkaHttpScalatestFetcherSpec extends Specification {

  "should fetch all class that extends LibraryAkkaHttpScalatestRouteDocSpec" in {
    val fetcher = new AkkaHttpScalatestFetcher

    fetcher.fetch("pl.iterators.baklava.akkahttpscalatest") should containTheSameElementsAs(
      List(
        EnrichedRouteRepresentation(TestData.routeRepresentation1, List(TestData.text11, TestData.text12).map(t => s"should $t")),
        EnrichedRouteRepresentation(TestData.routeRepresentation2, List(TestData.text21).map(t => s"should $t")),
      ))
  }

}
