package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*

import scala.jdk.CollectionConverters.*

class ResponseContentTypeMergeSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI response rendering with multiple content-types on the same status (regression for #63)") {

    it("preserves all content-types under a single ApiResponse instead of overwriting") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(
          jsonResponseCall(status = 200, desc = "JSON", body = """{"a":1}"""),
          xmlResponseCall(status = 200, desc = "XML", body = "<a>1</a>")
        )
      )

      val content = openAPI.getPaths.get("/items").getGet.getResponses.get("200").getContent
      content.keySet.asScala.toList.sorted shouldBe List("application/json", "application/xml")
      content.get("application/json").getExamples.get("JSON").getValue.toString should include("\"a\"")
      content.get("application/xml").getExamples.get("XML").getValue shouldBe "<a>1</a>"
    }

    it("merges response description and headers across all content-types for a status") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(
          jsonResponseCall(status = 200, desc = "JSON", body = "{}"),
          xmlResponseCall(status = 200, desc = "XML", body = "<x/>")
        )
      )

      openAPI.getPaths.get("/items").getGet.getResponses.get("200").getDescription shouldBe "JSON / XML"
    }
  }

  private def jsonResponseCall(status: Int, desc: String, body: String): BaklavaSerializableCall =
    responseCall(status, desc, body, Some("application/json"))

  private def xmlResponseCall(status: Int, desc: String, body: String): BaklavaSerializableCall =
    responseCall(status, desc, body, Some("application/xml"))

  private def responseCall(status: Int, desc: String, body: String, contentType: Option[String]): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = "/items",
        path = "/items",
        pathDescription = None,
        pathSummary = None,
        method = Some(BaklavaHttpMethod("GET")),
        operationDescription = None,
        operationSummary = None,
        operationId = None,
        operationTags = Nil,
        securitySchemes = Nil,
        bodySchema = None,
        headersSeq = Nil,
        pathParametersSeq = Nil,
        queryParametersSeq = Nil,
        responseDescription = Some(desc),
        responseHeaders = Nil
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = BaklavaHttpStatus(status),
        headers = BaklavaHttpHeaders(Map.empty),
        requestBodyString = "",
        responseBodyString = body,
        requestContentType = None,
        responseContentType = contentType,
        bodySchema = Some(BaklavaSchemaSerializable(Schema.stringSchema))
      )
    )
}
