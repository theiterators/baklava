package pl.iterators.baklava.formatteropenapi.utils

import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.{Components, Paths}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import pl.iterators.baklava.core.model.{EnrichedRouteRepresentation, RouteRepresentation, RouteSecurity}
import pl.iterators.baklava.sprayjson.SprayJsonStringProvider
import pl.iterators.kebs.json.KebsSpray
import pl.iterators.kebs.jsonschema.{KebsJsonSchema, KebsJsonSchemaPredefs}
import pl.iterators.kebs.scalacheck.{KebsArbitraryPredefs, KebsScalacheckGenerators}
import spray.json._

import scala.collection.JavaConverters._

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
      with SprayJsonStringProvider
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

    "properly pass enriched and extended descriptions" in new TestCase {
      val input = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 1", "GET", "/path1"),
          List("when Ok then Ok", "when NotFound than NotFound", "test")
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 2", "GET", "/path2"),
          List("when Ok then not Ok", "when InternalServerError then Internal Server Error")
        ),
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, Unit]("summary 3", "GET", "/path3", extendedDescription = Some("extendedDescription")),
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

      val path3 = openApi.getPaths.get("/path3")
      path3.getGet.getSummary shouldEqual "summary 3"
      path3.getGet.getDescription shouldEqual "extendedDescription"

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
      import pl.iterators.baklava.core.model.RouteSecurityGroup._

      val input1 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1", authentication = List()),
          List("Ok")
        )
      )
      val input2 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1", "GET", "/path1", authentication = List(RouteSecurity.Bearer())),
          List("Ok")
        )
      )
      val input3 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1",
                                                          "GET",
                                                          "/path1",
                                                          authentication = List(RouteSecurity.Bearer(), RouteSecurity.Basic())),
          List("Ok")
        )
      )
      val input4 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output]("summary 1",
                                                          "GET",
                                                          "/path1",
                                                          authentication =
                                                            List(RouteSecurity.Bearer(), RouteSecurity.Bearer("bearerAuth2"))),
          List("Ok")
        )
      )
      val input5 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output](
            "summary 1",
            "GET",
            "/path1",
            authentication = List(
              List(
                RouteSecurity.HeaderApiKey(name = "X-SECRET-API-KEY", schemaName = "secretApiKeyAuth"),
                RouteSecurity.HeaderApiKey(name = "X-ID-API-KEY", schemaName = "idApiKeyAuth")
              ),
              RouteSecurity.Bearer()
            )
          ),
          List("Ok")
        )
      )
      val input6 = List(
        EnrichedRouteRepresentation(
          RouteRepresentation[Unit, TestData.Path1Output](
            "summary 1",
            "GET",
            "/path1",
            authentication = List(
              RouteSecurity.HeaderApiKey("X-API-KEY"),
              RouteSecurity.QueryApiKey("api_key"),
              RouteSecurity.CookieApiKey("X-COOKIE-KEY-KEY")
            )
          ),
          List("Ok")
        )
      )

      val openApi1 = worker.generateOpenApi(input1)
      val openApi2 = worker.generateOpenApi(input2)
      val openApi3 = worker.generateOpenApi(input3)
      val openApi4 = worker.generateOpenApi(input4)
      val openApi5 = worker.generateOpenApi(input5)
      val openApi6 = worker.generateOpenApi(input6)

      openApi1.getPaths.get("/path1").getGet.getSecurity shouldEqual null
      openApi1.getComponents.getSecuritySchemes shouldEqual null

      openApi2.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 1
      openApi2.getPaths.get("/path1").getGet.getSecurity.get(0).get("bearerAuth") shouldEqual List.empty.asJava
      openApi2.getComponents.getSecuritySchemes.size() shouldEqual 1
      openApi2.getComponents.getSecuritySchemes.get("bearerAuth").getScheme shouldEqual "bearer"
      openApi2.getComponents.getSecuritySchemes.get("bearerAuth").getBearerFormat shouldEqual "JWT"
      openApi2.getComponents.getSecuritySchemes.get("bearerAuth").getType shouldEqual SecurityScheme.Type.HTTP

      openApi3.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 2
      openApi3.getPaths.get("/path1").getGet.getSecurity.get(0).get("bearerAuth") shouldEqual List.empty.asJava
      openApi3.getPaths.get("/path1").getGet.getSecurity.get(1).get("basicAuth") shouldEqual List.empty.asJava
      openApi3.getComponents.getSecuritySchemes.size() shouldEqual 2
      openApi3.getComponents.getSecuritySchemes.get("bearerAuth").getScheme shouldEqual "bearer"
      openApi3.getComponents.getSecuritySchemes.get("bearerAuth").getBearerFormat shouldEqual "JWT"
      openApi3.getComponents.getSecuritySchemes.get("bearerAuth").getType shouldEqual SecurityScheme.Type.HTTP
      openApi3.getComponents.getSecuritySchemes.get("basicAuth").getScheme shouldEqual "basic"
      openApi3.getComponents.getSecuritySchemes.get("basicAuth").getType shouldEqual SecurityScheme.Type.HTTP

      openApi4.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 2
      openApi4.getPaths.get("/path1").getGet.getSecurity.get(0).get("bearerAuth") shouldEqual List.empty.asJava
      openApi4.getPaths.get("/path1").getGet.getSecurity.get(1).get("bearerAuth2") shouldEqual List.empty.asJava
      openApi4.getComponents.getSecuritySchemes.size() shouldEqual 2
      openApi4.getComponents.getSecuritySchemes.get("bearerAuth").getScheme shouldEqual "bearer"
      openApi4.getComponents.getSecuritySchemes.get("bearerAuth").getBearerFormat shouldEqual "JWT"
      openApi4.getComponents.getSecuritySchemes.get("bearerAuth").getType shouldEqual SecurityScheme.Type.HTTP
      openApi4.getComponents.getSecuritySchemes.get("bearerAuth2").getScheme shouldEqual "bearer"
      openApi4.getComponents.getSecuritySchemes.get("bearerAuth2").getBearerFormat shouldEqual "JWT"
      openApi4.getComponents.getSecuritySchemes.get("bearerAuth2").getType shouldEqual SecurityScheme.Type.HTTP

      openApi5.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 2
      openApi5.getPaths.get("/path1").getGet.getSecurity.get(0).get("secretApiKeyAuth") shouldEqual List.empty.asJava
      openApi5.getPaths.get("/path1").getGet.getSecurity.get(0).get("idApiKeyAuth") shouldEqual List.empty.asJava
      openApi5.getPaths.get("/path1").getGet.getSecurity.get(1).get("bearerAuth") shouldEqual List.empty.asJava
      openApi5.getComponents.getSecuritySchemes.size() shouldEqual 3
      openApi5.getComponents.getSecuritySchemes.get("bearerAuth").getScheme shouldEqual "bearer"
      openApi5.getComponents.getSecuritySchemes.get("bearerAuth").getBearerFormat shouldEqual "JWT"
      openApi5.getComponents.getSecuritySchemes.get("bearerAuth").getType shouldEqual SecurityScheme.Type.HTTP
      openApi5.getComponents.getSecuritySchemes.get("idApiKeyAuth").getType shouldEqual SecurityScheme.Type.APIKEY
      openApi5.getComponents.getSecuritySchemes.get("idApiKeyAuth").getIn shouldEqual SecurityScheme.In.HEADER
      openApi5.getComponents.getSecuritySchemes.get("idApiKeyAuth").getName shouldEqual "X-ID-API-KEY"
      openApi5.getComponents.getSecuritySchemes.get("secretApiKeyAuth").getType shouldEqual SecurityScheme.Type.APIKEY
      openApi5.getComponents.getSecuritySchemes.get("secretApiKeyAuth").getIn shouldEqual SecurityScheme.In.HEADER
      openApi5.getComponents.getSecuritySchemes.get("secretApiKeyAuth").getName shouldEqual "X-SECRET-API-KEY"

      openApi6.getPaths.get("/path1").getGet.getSecurity.size() shouldEqual 3
      openApi6.getPaths.get("/path1").getGet.getSecurity.get(0).get("headerApiKeyAuth") shouldEqual List.empty.asJava
      openApi6.getPaths.get("/path1").getGet.getSecurity.get(1).get("queryApiKeyAuth") shouldEqual List.empty.asJava
      openApi6.getPaths.get("/path1").getGet.getSecurity.get(2).get("cookieApiKeyAuth") shouldEqual List.empty.asJava
      openApi6.getComponents.getSecuritySchemes.size() shouldEqual 3
      openApi6.getComponents.getSecuritySchemes.get("headerApiKeyAuth").getType shouldEqual SecurityScheme.Type.APIKEY
      openApi6.getComponents.getSecuritySchemes.get("headerApiKeyAuth").getIn shouldEqual SecurityScheme.In.HEADER
      openApi6.getComponents.getSecuritySchemes.get("headerApiKeyAuth").getName shouldEqual "X-API-KEY"
      openApi6.getComponents.getSecuritySchemes.get("queryApiKeyAuth").getType shouldEqual SecurityScheme.Type.APIKEY
      openApi6.getComponents.getSecuritySchemes.get("queryApiKeyAuth").getIn shouldEqual SecurityScheme.In.QUERY
      openApi6.getComponents.getSecuritySchemes.get("queryApiKeyAuth").getName shouldEqual "api_key"
      openApi6.getComponents.getSecuritySchemes.get("cookieApiKeyAuth").getType shouldEqual SecurityScheme.Type.APIKEY
      openApi6.getComponents.getSecuritySchemes.get("cookieApiKeyAuth").getIn shouldEqual SecurityScheme.In.COOKIE
      openApi6.getComponents.getSecuritySchemes.get("cookieApiKeyAuth").getName shouldEqual "X-COOKIE-KEY-KEY"
    }
  }

}
