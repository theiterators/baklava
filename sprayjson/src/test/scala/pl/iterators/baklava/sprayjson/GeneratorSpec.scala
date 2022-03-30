package pl.iterators.baklava.sprayjson

import org.specs2.mutable.Specification
import pl.iterators.baklava.core.Generator
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.formatters.Formatter
import pl.iterators.baklava.core.model._
import pl.iterators.kebs.jsonschema._
import pl.iterators.kebs.scalacheck._
import spray.json.DefaultJsonProtocol

import scala.util.Random

object StaticTestState {

  var testFetcherCreated: Boolean                                    = false
  var testFetcherLastMainPackageName                                 = ""
  var testFetcherReturnList: List[EnrichedRouteRepresentation[_, _]] = Nil

  var testFormatterCreated: Boolean                                   = false
  var testFormatterLastOutputPath: String                             = ""
  var testFormatterInputList: List[EnrichedRouteRepresentation[_, _]] = Nil

}

class TestFetcher
    extends Fetcher
    with SprayJsonStringProvider
    with DefaultJsonProtocol
    with KebsJsonSchema
    with KebsArbitraryPredefs
    with KebsJsonSchemaPredefs
    with KebsScalacheckGenerators {

  StaticTestState.testFetcherCreated = true

  override def fetch(mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    StaticTestState.testFetcherLastMainPackageName = mainPackageName

    StaticTestState.testFetcherReturnList = List(
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

    StaticTestState.testFetcherReturnList
  }
}

class TestFormatter extends Formatter {
  StaticTestState.testFormatterCreated = true

  override def generate(outputPath: String, printableList: List[EnrichedRouteRepresentation[_, _]]): Unit = {
    StaticTestState.testFormatterLastOutputPath = outputPath
    StaticTestState.testFormatterInputList = printableList
    ()
  }
}

class GeneratorSpec extends Specification {

  "should call fetcher and formatter with proper params" in {
    1 shouldEqual 1

    val mainPackageName: String = "mainPackageName"
    val outputDir: String       = "outputDir"
    val fetcherName: String     = "TestFetcher"
    val formatterName: String   = "TestFormatter"

    Generator.generate(mainPackageName, outputDir, fetcherName, formatterName)

    StaticTestState.testFetcherCreated shouldEqual true
    StaticTestState.testFetcherLastMainPackageName shouldEqual mainPackageName

    StaticTestState.testFormatterCreated shouldEqual true
    StaticTestState.testFormatterLastOutputPath shouldEqual outputDir

    StaticTestState.testFetcherReturnList shouldEqual StaticTestState.testFormatterInputList
  }
}
