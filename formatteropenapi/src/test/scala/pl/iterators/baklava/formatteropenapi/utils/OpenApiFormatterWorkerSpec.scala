package pl.iterators.baklava.formatteropenapi.utils

import io.swagger.v3.oas.models.{Components, OpenAPI, Paths}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation}
import pl.iterators.kebs.json.KebsSpray
import pl.iterators.kebs.jsonschema.{KebsJsonSchema, KebsJsonSchemaPredefs}
import pl.iterators.kebs.scalacheck.{KebsArbitraryPredefs, KebsScalacheckGenerators}
import spray.json._

import scala.jdk.CollectionConverters._

object TestData {
  case class Path1Output(p1Int: Int, p1String: String)

  case class Path2Input(p2Int: Int, p2String: String)

  case class Path3Input(p3Int: Int, p3String: String)

  case class Path3Output(p3OInt: Int, p3OString: String)
}

class OpenApiFormatterWorkerSpec extends Specification {

  trait TestCase
      extends Scope
      with DefaultJsonProtocol
      with KebsSpray
      with KebsJsonSchema
      with KebsArbitraryPredefs
      with KebsJsonSchemaPredefs
      with KebsScalacheckGenerators {

    val converter = new JsonSchemaToSwaggerSchemaWorker
    val worker    = new OpenApiFormatterWorker(converter)
  }

  "converter" should {
    "works for empty list" in new TestCase {
      val openApi = worker.generateOpenApi(Nil)
      openApi.getPaths shouldEqual new Paths
      openApi.getComponents shouldEqual new Components
    }

    "properly split groups paths" in new TestCase {
      val input = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 1", "GET", "/path1"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 2", "POST", "/path1"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 3", "GET", "/path2/abc"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 4", "POST", "/path2/abc"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 5", "PATCH", "/path2/abc"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 6", "DELETE", "/path2/abc"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 7", "PUT", "/path2/abc"),
          Nil
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 8", "PUT", "/abc/path3"),
          Nil
        ),
      )

      val openApi = worker.generateOpenApi(input)

      val path1 = openApi.getPaths.get("/path1")
      path1.getGet.getSummary shouldEqual "summary 1"
      path1.getPost.getSummary shouldEqual "summary 2"

      val path2 = openApi.getPaths.get("/path2/abc")
      path2.getGet.getSummary shouldEqual "summary 3"
      path2.getPost.getSummary shouldEqual "summary 4"
      path2.getPatch.getSummary shouldEqual "summary 5"
      path2.getDelete.getSummary shouldEqual "summary 6"
      path2.getPut.getSummary shouldEqual "summary 7"

      val path3 = openApi.getPaths.get("/abc/path3")
      path3.getPut.getSummary shouldEqual "summary 8"

      openApi.getComponents shouldEqual new Components
    }

    "properly pass enriched descriptions" in new TestCase {
      val input = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 1", "GET", "/path1"),
          List("when Ok then Ok", "when NotFound than NotFound", "test")
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 2", "GET", "/path2"),
          List("when Ok then not Ok", "when InternalServerError then Internal Server Error")
        )
      )

      val openApi = worker.generateOpenApi(input)

      val path1 = openApi.getPaths.get("/path1")
      path1.getGet.getSummary shouldEqual "summary 1"
      path1.getGet.getDescription shouldEqual "when Ok then Ok\nwhen NotFound than NotFound\ntest"

      val path2 = openApi.getPaths.get("/path2")
      path2.getGet.getSummary shouldEqual "summary 2"
      path2.getGet.getDescription shouldEqual "when Ok then not Ok\nwhen InternalServerError then Internal Server Error"

      openApi.getComponents shouldEqual new Components
    }

    "properly pass schema refs for input and output classes" in new TestCase {
      val input = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1"),
          List("Ok")
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[TestData.Path2Input, Unit]("summary 2", "PUT", "/path2"),
          List("Ok")
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[TestData.Path3Input, TestData.Path3Output]("summary 3", "POST", "/path3"),
          List("Ok")
        )
      )

      val openApi = worker.generateOpenApi(input)

      val path1 = openApi.getPaths.get("/path1")
      path1.getGet.getSummary shouldEqual "summary 1"
      val path1OutputMt = path1.getGet.getResponses.get("200").getContent.get("application/json")
      path1OutputMt.getSchema.getType shouldEqual "object"
      path1OutputMt.getSchema.get$ref shouldEqual "#/components/schemas/pl.iterators.baklava.formatteropenapi.utils.TestData.Path1Output"
      path1OutputMt.getExamples.get("minimal").getValue.toString.parseJson.convertTo[TestData.Path1Output] shouldNotEqual
        path1OutputMt.getExamples.get("maximal").getValue.toString.parseJson.convertTo[TestData.Path1Output]

      val path2 = openApi.getPaths.get("/path2")
      path2.getPut.getSummary shouldEqual "summary 2"
      val path2InputMt = path2.getPut.getRequestBody.getContent.get("application/json")
      path2InputMt.getSchema.getType shouldEqual "object"
      path2InputMt.getSchema.get$ref shouldEqual "#/components/schemas/pl.iterators.baklava.formatteropenapi.utils.TestData.Path2Input"
      path2InputMt.getExamples.get("minimal").getValue.toString.parseJson.convertTo[TestData.Path2Input] shouldNotEqual
        path2InputMt.getExamples.get("maximal").getValue.toString.parseJson.convertTo[TestData.Path2Input]

      val path3 = openApi.getPaths.get("/path3")
      path3.getPost.getSummary shouldEqual "summary 3"
      val path3InputMt = path3.getPost.getRequestBody.getContent.get("application/json")
      path3InputMt.getSchema.getType shouldEqual "object"
      path3InputMt.getSchema.get$ref shouldEqual "#/components/schemas/pl.iterators.baklava.formatteropenapi.utils.TestData.Path3Input"
      path3InputMt.getExamples.get("minimal").getValue.toString.parseJson.convertTo[TestData.Path3Input] shouldNotEqual
        path3InputMt.getExamples.get("maximal").getValue.toString.parseJson.convertTo[TestData.Path3Input]
      val path3OutputMt = path3.getPost.getResponses.get("200").getContent.get("application/json")
      path3OutputMt.getSchema.getType shouldEqual "object"
      path3OutputMt.getSchema.get$ref shouldEqual "#/components/schemas/pl.iterators.baklava.formatteropenapi.utils.TestData.Path3Output"
      path3OutputMt.getExamples.get("minimal").getValue.toString.parseJson.convertTo[TestData.Path3Output] shouldNotEqual
        path3OutputMt.getExamples.get("maximal").getValue.toString.parseJson.convertTo[TestData.Path3Output]

      val schemas = openApi.getComponents.getSchemas
      schemas.get("pl.iterators.baklava.formatteropenapi.utils.TestData.Path1Output") shouldEqual
        converter.convert(genericJsonSchemaWrapper[TestData.Path1Output].schema)
      schemas.get("pl.iterators.baklava.formatteropenapi.utils.TestData.Path2Input") shouldEqual
        converter.convert(genericJsonSchemaWrapper[TestData.Path2Input].schema)
      schemas.get("pl.iterators.baklava.formatteropenapi.utils.TestData.Path3Input") shouldEqual
        converter.convert(genericJsonSchemaWrapper[TestData.Path3Input].schema)
      schemas.get("pl.iterators.baklava.formatteropenapi.utils.TestData.Path3Output") shouldEqual
        converter.convert(genericJsonSchemaWrapper[TestData.Path3Output].schema)
    }

    "properly pass authentication details" in new TestCase {
      val input1 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1", authentication = None),
          List("Ok")
        )
      )
      val input2 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1", authentication = Some(List("Bearer"))),
          List("Ok")
        )
      )
      val input3 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1", authentication = Some(List("Bearer", "Basic"))),
          List("Ok")
        )
      )
      val input4 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1", authentication = Some(List("Invalid"))),
          List("Ok")
        )
      )

      val openApi1 = worker.generateOpenApi(input1)
      val openApi2 = worker.generateOpenApi(input2)
      val openApi3 = worker.generateOpenApi(input3)
      val openApi4 = worker.generateOpenApi(input4)

      openApi1.getPaths.get("/path1").getGet.getSecurity shouldEqual null

      openApi2.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 1
      openApi2.getPaths.get("/path1").getGet.getSecurity.get(0).get("bearerAuth") shouldEqual List.empty.asJava

      openApi3.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 2
      openApi3.getPaths.get("/path1").getGet.getSecurity.get(0).get("bearerAuth") shouldEqual List.empty.asJava
      openApi3.getPaths.get("/path1").getGet.getSecurity.get(1).get("basicAuth") shouldEqual List.empty.asJava

      openApi4.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 0
    }
  }

}
