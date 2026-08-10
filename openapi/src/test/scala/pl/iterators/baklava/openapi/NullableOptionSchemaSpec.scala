package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import scala.jdk.CollectionConverters.*

// Regression for #131: Option fields must emit `nullable: true` so captured null values validate.
class NullableOptionSchemaSpec extends AnyFunSpec with Matchers {

  case class ErrorResponse(message: String, details: Option[String])

  sealed trait Status
  case object Active   extends Status
  case object Archived extends Status
  case class Project(name: String, status: Option[Status])

  describe("OpenAPI schema emission for Option fields") {

    it("emits nullable: true for Option properties and omits it for required ones") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(errorCall("""{"message":"boom","details":null}""")))

      val schema =
        openAPI.getPaths.get("/errors").getGet.getResponses.get("500").getContent.get("application/json").getSchema
      schema.getProperties.get("details").getNullable shouldBe java.lang.Boolean.TRUE
      Option(schema.getProperties.get("message").getNullable) shouldBe None
    }

    it("serializes nullable: true in YAML so null-valued examples validate against the schema") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(errorCall("""{"message":"boom","details":null}""")))

      Yaml.pretty(openAPI) should include("nullable: true")
    }

    it("appends null to the enum values of a nullable enum schema (OAS 3.0 requires it to validate null)") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(errorCall("""{"name":"x","status":null}""", schema = BaklavaSchemaSerializable(implicitly[Schema[Project]])))
      )

      val statusSchema =
        openAPI.getPaths
          .get("/errors")
          .getGet
          .getResponses
          .get("500")
          .getContent
          .get("application/json")
          .getSchema
          .getProperties
          .get("status")
      statusSchema.getNullable shouldBe java.lang.Boolean.TRUE
      statusSchema.getEnum.asScala should contain(null)
      statusSchema.getEnum.asScala should contain allOf ("Active", "Archived")
    }
  }

  private def errorCall(
      body: String,
      schema: BaklavaSchemaSerializable = BaklavaSchemaSerializable(implicitly[Schema[ErrorResponse]])
  ): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = "/errors",
        path = "/errors",
        pathDescription = None,
        pathSummary = None,
        method = Some(Method("GET")),
        operationDescription = None,
        operationSummary = None,
        operationId = None,
        operationTags = Nil,
        securitySchemes = Nil,
        bodySchema = None,
        bodyString = "",
        headersSeq = Nil,
        pathParametersSeq = Nil,
        queryParametersSeq = Nil,
        responseDescription = Some("Server error"),
        responseHeaders = Nil
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(500),
        headers = Seq.empty,
        bodyString = body,
        requestContentType = None,
        responseContentType = Some("application/json"),
        bodySchema = Some(schema)
      )
    )
}
