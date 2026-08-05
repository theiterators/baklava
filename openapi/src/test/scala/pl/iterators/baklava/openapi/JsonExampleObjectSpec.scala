package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import scala.jdk.CollectionConverters.*

// Regression for #120: JSON examples must be structured objects, not re-printed strings.
class JsonExampleObjectSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI example values for application/json bodies") {

    it("emits response examples as structured objects, preserving key order and value types") {
      val openAPI = new OpenAPI()
      val body    =
        """{"message":"Not Found","documentation_url":"https://docs.github.com/rest","status":"404","count":3,"active":true,"tags":["a","b"],"nested":{"x":1.5}}"""
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(responseCall(desc = "User not found", body = body)))

      val example = openAPI.getPaths
        .get("/items")
        .getGet
        .getResponses
        .get("200")
        .getContent
        .get("application/json")
        .getExamples
        .get("User not found")

      example.getValue shouldBe a[java.util.Map[?, ?]]
      val map = example.getValue.asInstanceOf[java.util.Map[String, Object]]
      map.keySet.asScala.toList shouldBe List("message", "documentation_url", "status", "count", "active", "tags", "nested")
      map.get("message") shouldBe "Not Found"
      map.get("status") shouldBe "404"
      map.get("count") shouldBe java.lang.Long.valueOf(3)
      map.get("active") shouldBe java.lang.Boolean.TRUE
      map.get("tags").asInstanceOf[java.util.List[Object]].asScala.toList shouldBe List("a", "b")
      map.get("nested").asInstanceOf[java.util.Map[String, Object]].get("x") shouldBe new java.math.BigDecimal("1.5")
    }

    it("serializes response examples structurally in YAML, not as an escaped string blob") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(responseCall(desc = "User not found", body = """{"message":"Not Found"}"""))
      )

      val yaml = Yaml.pretty(openAPI)
      yaml should include("message: Not Found")
      yaml should not include "\"message\""
    }

    it("emits request body examples as structured objects") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(requestBodyCall(desc = "Create user", requestBody = """{"name":"John","age":42}"""))
      )

      val example = openAPI.getPaths
        .get("/items")
        .getPost
        .getRequestBody
        .getContent
        .get("application/json")
        .getExamples
        .get("Create user")

      example.getValue shouldBe a[java.util.Map[?, ?]]
      val map = example.getValue.asInstanceOf[java.util.Map[String, Object]]
      map.get("name") shouldBe "John"
      map.get("age") shouldBe java.lang.Long.valueOf(42)
    }

    it("emits structured examples for +json structured-syntax suffix media types (regression for #129)") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(
          responseCall(
            desc = "Validation failed",
            body = """{"title":"Invalid request"}""",
            contentType = Some("application/problem+json")
          )
        )
      )

      val example = openAPI.getPaths
        .get("/items")
        .getGet
        .getResponses
        .get("200")
        .getContent
        .get("application/problem+json")
        .getExamples
        .get("Validation failed")

      example.getValue shouldBe a[java.util.Map[?, ?]]
      example.getValue.asInstanceOf[java.util.Map[String, Object]].get("title") shouldBe "Invalid request"
    }

    it("keeps non-JSON content types as raw strings") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(responseCall(desc = "plain", body = "just text", contentType = Some("text/plain")))
      )

      val example =
        openAPI.getPaths.get("/items").getGet.getResponses.get("200").getContent.get("text/plain").getExamples.get("plain")
      example.getValue shouldBe "just text"
    }

    it("falls back to the raw string when an application/json body does not parse") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(responseCall(desc = "broken", body = "{not json"))
      )

      val example =
        openAPI.getPaths.get("/items").getGet.getResponses.get("200").getContent.get("application/json").getExamples.get("broken")
      example.getValue shouldBe "{not json"
    }
  }

  private def responseCall(
      desc: String,
      body: String,
      contentType: Option[String] = Some("application/json")
  ): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = requestContext(desc, method = "GET"),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(200),
        headers = Seq.empty,
        bodyString = body,
        requestContentType = None,
        responseContentType = contentType,
        bodySchema = Some(BaklavaSchemaSerializable(Schema.stringSchema))
      )
    )

  private def requestBodyCall(desc: String, requestBody: String): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = requestContext(desc, method = "POST", bodyString = requestBody),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(200),
        headers = Seq.empty,
        bodyString = "",
        requestContentType = Some("application/json"),
        responseContentType = None,
        bodySchema = None
      )
    )

  private def requestContext(desc: String, method: String, bodyString: String = ""): BaklavaRequestContextSerializable =
    BaklavaRequestContextSerializable(
      symbolicPath = "/items",
      path = "/items",
      pathDescription = None,
      pathSummary = None,
      method = Some(Method(method)),
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Nil,
      securitySchemes = Nil,
      bodySchema = None,
      bodyString = bodyString,
      headersSeq = Nil,
      pathParametersSeq = Nil,
      queryParametersSeq = Nil,
      responseDescription = Some(desc),
      responseHeaders = Nil
    )
}
