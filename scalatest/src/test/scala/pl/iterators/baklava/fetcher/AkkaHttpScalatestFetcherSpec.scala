package pl.iterators.baklava.fetcher

import org.reflections.Reflections
import org.scalatest.flatspec.{AnyFlatSpec, AnyFlatSpecLike}
import org.scalatest.matchers.should.Matchers
import org.specs2.mutable.Specification
import pl.iterators.baklava.akkahttp.AkkaHttpRouteBaklavaSpec
import pl.iterators.baklava.circe.CirceJsonStringProvider
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.jsonschema.{KebsJsonSchema, KebsJsonSchemaPredefs}
import pl.iterators.kebs.scalacheck.{KebsArbitraryPredefs, KebsScalacheckGenerators}

trait LibraryTestSpec extends ScalatestRouteBaklavaSpec with AkkaHttpRouteBaklavaSpec with AnyFlatSpecLike with Matchers {
  override def shutdownSpec(): Unit = ()
}

object ScalatestTestData
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

class ScalatestInnerTest1 extends AnyFlatSpec with LibraryTestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = ScalatestTestData.routeRepresentation1

  it should ScalatestTestData.text11 in {
    1 shouldEqual 1
  }

  it should ScalatestTestData.text12 in {
    1 shouldEqual 1
  }
}

class ScalatestInnerTest2 extends AnyFlatSpec with LibraryTestSpec {
  override val routeRepresentation: RouteRepresentation[Unit, Unit] = ScalatestTestData.routeRepresentation2

  it should ScalatestTestData.text21 in {
    1 shouldEqual 1
  }

}

class AkkaHttpScalatestFetcherSpec extends Specification {

  "should fetch all class that extends ScalatestRouteBaklavaSpec" in {
    val fetcher     = new ScalatestFetcher
    val reflections = new Reflections("pl.iterators.baklava")

    fetcher.fetch(reflections, "pl.iterators.baklava") should containTheSameElementsAs(
      List(
        EnrichedRouteRepresentation(ScalatestTestData.routeRepresentation1,
                                    List(ScalatestTestData.text11, ScalatestTestData.text12).map(t => s"should $t")),
        EnrichedRouteRepresentation(ScalatestTestData.routeRepresentation2, List(ScalatestTestData.text21).map(t => s"should $t")),
      ))
  }

}
