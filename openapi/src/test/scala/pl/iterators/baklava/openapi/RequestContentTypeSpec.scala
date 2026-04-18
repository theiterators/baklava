package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import scala.jdk.CollectionConverters.*

class RequestContentTypeSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI request body rendering (regression for #64)") {

    it("emits the request content-type entry even when no body schema is captured") {
      val call = BaklavaSerializableCall(
        request = BaklavaRequestContextSerializable(
          symbolicPath = "/upload",
          path = "/upload",
          pathDescription = None,
          pathSummary = None,
          method = Some(Method("POST")),
          operationDescription = None,
          operationSummary = None,
          operationId = None,
          operationTags = Nil,
          securitySchemes = Nil,
          bodySchema = None,
          headersSeq = Nil,
          pathParametersSeq = Nil,
          queryParametersSeq = Nil,
          responseDescription = Some("uploaded"),
          responseHeaders = Nil
        ),
        response = BaklavaResponseContextSerializable(
          protocol = BaklavaHttpProtocol("HTTP/1.1"),
          status = StatusCode(201),
          headers = Seq.empty,
          requestBodyString = "raw binary payload",
          responseBodyString = "",
          requestContentType = Some("application/octet-stream"),
          responseContentType = None,
          bodySchema = None
        )
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val requestBody = openAPI.getPaths.get("/upload").getPost.getRequestBody
      requestBody should not be null
      requestBody.getContent.keySet.asScala should contain("application/octet-stream")

      val mediaType = requestBody.getContent.get("application/octet-stream")
      mediaType.getSchema shouldBe null // no schema was captured
      mediaType.getExamples.size shouldBe 1
      mediaType.getExamples.get("uploaded").getValue shouldBe "raw binary payload"
    }

    it("emits contentType-only when body text is absent (e.g. Content-Length: 0 POST)") {
      val call = BaklavaSerializableCall(
        request = BaklavaRequestContextSerializable(
          symbolicPath = "/trigger",
          path = "/trigger",
          pathDescription = None,
          pathSummary = None,
          method = Some(Method("POST")),
          operationDescription = None,
          operationSummary = None,
          operationId = None,
          operationTags = Nil,
          securitySchemes = Nil,
          bodySchema = None,
          headersSeq = Nil,
          pathParametersSeq = Nil,
          queryParametersSeq = Nil,
          responseDescription = None,
          responseHeaders = Nil
        ),
        response = BaklavaResponseContextSerializable(
          protocol = BaklavaHttpProtocol("HTTP/1.1"),
          status = StatusCode(202),
          headers = Seq.empty,
          requestBodyString = "",
          responseBodyString = "",
          requestContentType = Some("application/json"),
          responseContentType = None,
          bodySchema = None
        )
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val requestBody = openAPI.getPaths.get("/trigger").getPost.getRequestBody
      requestBody should not be null
      requestBody.getContent.keySet.asScala should contain("application/json")
      Option(requestBody.getContent.get("application/json").getExamples).getOrElse(java.util.Collections.emptyMap()).size shouldBe 0
    }

    it("does not emit a request body at all when contentType is absent and no body text was captured") {
      val call = BaklavaSerializableCall(
        request = BaklavaRequestContextSerializable(
          symbolicPath = "/ping",
          path = "/ping",
          pathDescription = None,
          pathSummary = None,
          method = Some(Method("GET")),
          operationDescription = None,
          operationSummary = None,
          operationId = None,
          operationTags = Nil,
          securitySchemes = Nil,
          bodySchema = None,
          headersSeq = Nil,
          pathParametersSeq = Nil,
          queryParametersSeq = Nil,
          responseDescription = None,
          responseHeaders = Nil
        ),
        response = BaklavaResponseContextSerializable(
          protocol = BaklavaHttpProtocol("HTTP/1.1"),
          status = StatusCode(200),
          headers = Seq.empty,
          requestBodyString = "",
          responseBodyString = "",
          requestContentType = None,
          responseContentType = None,
          bodySchema = None
        )
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      openAPI.getPaths.get("/ping").getGet.getRequestBody shouldBe null
    }
  }
}
