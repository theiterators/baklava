package pl.iterators.baklava.generator

import org.reflections.Reflections
import org.specs2.mutable.Specification
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.model._
import pl.iterators.baklava.formatter.Formatter
import pl.iterators.baklava.sprayjson.SprayJsonStringProvider
import pl.iterators.kebs.jsonschema._
import pl.iterators.kebs.scalacheck._
import spray.json.DefaultJsonProtocol

import scala.util.Random

object SprayStaticTestState {

  var testFetcherCreated: Boolean                                    = false
  var testFetcherLastMainPackageName                                 = ""
  var testFetcherReturnList: List[EnrichedRouteRepresentation[_, _]] = Nil

  var testFormatterCreated: Boolean                                   = false
  var testFormatterLastOutputPath: String                             = ""
  var testFormatterInputList: List[EnrichedRouteRepresentation[_, _]] = Nil

}

class SprayTestFetcher
    extends Fetcher
    with SprayJsonStringProvider
    with DefaultJsonProtocol
    with KebsJsonSchema
    with KebsArbitraryPredefs
    with KebsJsonSchemaPredefs
    with KebsScalacheckGenerators {

  SprayStaticTestState.testFetcherCreated = true

  override def fetch(reflections: Reflections, mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    SprayStaticTestState.testFetcherLastMainPackageName = mainPackageName

    SprayStaticTestState.testFetcherReturnList = List(
      EnrichedRouteRepresentation(
        RouteRepresentation[Unit, Unit](Random.nextString(10), Random.nextString(10), Random.nextString(10)),
        Nil
      ),
      EnrichedRouteRepresentation(
        RouteRepresentation[Unit, Unit](Random.nextString(10), Random.nextString(10), Random.nextString(10)),
        Nil
      ),
      EnrichedRouteRepresentation(
        RouteRepresentation[Unit, Unit](Random.nextString(10), Random.nextString(10), Random.nextString(10)),
        Nil
      )
    )

    SprayStaticTestState.testFetcherReturnList
  }
}

class SprayTestFormatter extends Formatter {
  SprayStaticTestState.testFormatterCreated = true

  override def generate(outputPath: String, printableList: List[EnrichedRouteRepresentation[_, _]]): Unit = {
    SprayStaticTestState.testFormatterLastOutputPath = outputPath
    SprayStaticTestState.testFormatterInputList = printableList
    ()
  }
}

class GeneratorSpraySpec extends Specification {

  "should call fetcher and formatter with proper params" in {
    1 shouldEqual 1

    val mainPackageName: String = "mainPackageName"
    val outputDir: String       = "outputDir"
    val fetcherName: String     = "SprayTestFetcher"
    val formatterName: String   = "SprayTestFormatter"

    Generator.generate(mainPackageName, outputDir, fetcherName, Seq(formatterName))

    SprayStaticTestState.testFetcherCreated shouldEqual true
    SprayStaticTestState.testFetcherLastMainPackageName shouldEqual mainPackageName

    SprayStaticTestState.testFormatterCreated shouldEqual true
    SprayStaticTestState.testFormatterLastOutputPath shouldEqual outputDir

    SprayStaticTestState.testFetcherReturnList shouldEqual SprayStaticTestState.testFormatterInputList
  }
}
