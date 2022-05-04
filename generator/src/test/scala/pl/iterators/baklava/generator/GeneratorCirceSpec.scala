package pl.iterators.baklava.generator

import org.reflections.Reflections
import org.specs2.mutable.Specification
import pl.iterators.baklava.circe.CirceJsonStringProvider
import pl.iterators.baklava.core.fetchers.Fetcher
import pl.iterators.baklava.core.model._
import pl.iterators.baklava.formatter.Formatter
import pl.iterators.kebs.jsonschema._
import pl.iterators.kebs.scalacheck._

import scala.util.Random

object CirceStaticTestState {

  var testFetcherCreated: Boolean                                    = false
  var testFetcherLastMainPackageName                                 = ""
  var testFetcherReturnList: List[EnrichedRouteRepresentation[_, _]] = Nil

  var testFormatterCreated: Boolean                                   = false
  var testFormatterLastOutputPath: String                             = ""
  var testFormatterInputList: List[EnrichedRouteRepresentation[_, _]] = Nil

}

class CirceTestFetcher
    extends Fetcher
    with CirceJsonStringProvider
    with KebsJsonSchema
    with KebsArbitraryPredefs
    with KebsJsonSchemaPredefs
    with KebsScalacheckGenerators {

  CirceStaticTestState.testFetcherCreated = true

  override def fetch(reflections: Reflections, mainPackageName: String): List[EnrichedRouteRepresentation[_, _]] = {
    CirceStaticTestState.testFetcherLastMainPackageName = mainPackageName

    CirceStaticTestState.testFetcherReturnList = List(
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

    CirceStaticTestState.testFetcherReturnList
  }
}

class TestFormatter extends Formatter {
  CirceStaticTestState.testFormatterCreated = true

  override def generate(outputPath: String, printableList: List[EnrichedRouteRepresentation[_, _]]): Unit = {
    CirceStaticTestState.testFormatterLastOutputPath = outputPath
    CirceStaticTestState.testFormatterInputList = printableList
    ()
  }
}

class GeneratorCirceSpec extends Specification {

  "should call fetcher and formatter with proper params" in {
    1 shouldEqual 1

    val mainPackageName: String = "mainPackageName"
    val outputDir: String       = "outputDir"
    val fetcherName: String     = "CirceTestFetcher"
    val formatterName: String   = "TestFormatter"

    Generator.generate(mainPackageName, outputDir, fetcherName, Seq(formatterName))

    CirceStaticTestState.testFetcherCreated shouldEqual true
    CirceStaticTestState.testFetcherLastMainPackageName shouldEqual mainPackageName

    CirceStaticTestState.testFormatterCreated shouldEqual true
    CirceStaticTestState.testFormatterLastOutputPath shouldEqual outputDir

    CirceStaticTestState.testFetcherReturnList shouldEqual CirceStaticTestState.testFormatterInputList
  }
}
