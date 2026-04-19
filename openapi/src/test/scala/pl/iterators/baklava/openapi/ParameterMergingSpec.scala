package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import scala.jdk.CollectionConverters.*

/** Regression suite for #66: parameter declarations from non-head calls used to be silently dropped because the generator read only
  * `calls.head.request.*Seq`. Now it merges across every call in the (path, method) group.
  */
class ParameterMergingSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI parameter merging across multiple supports (regression for #66)") {

    it("merges query parameters from every variant, alphabetically sorted, deduped by name") {
      val call1 = synthCall(queryParams = Seq("q" -> stringSchema))
      val call2 = synthCall(queryParams = Seq("limit" -> intSchema, "q" -> stringSchema))

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call1, call2))

      val params = openAPI.getPaths.get("/search").getGet.getParameters.asScala.filter(_.getIn == "query").map(_.getName).toList
      params shouldBe List("limit", "q")
    }

    it("merges path parameters from every variant") {
      val call1 = synthCall(path = "/items/{id}", pathParams = Seq("id" -> stringSchema))
      val call2 = synthCall(path = "/items/{id}", pathParams = Seq("id" -> stringSchema))

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call1, call2))

      val params = openAPI.getPaths.get("/items/{id}").getGet.getParameters.asScala.filter(_.getIn == "path").map(_.getName).toList
      params shouldBe List("id")
    }

    it("merges request headers case-insensitively, excluding Content-Type/Accept/Authorization") {
      val call1 = synthCall(headers = Seq("X-Request-Id" -> stringSchema))
      val call2 = synthCall(headers =
        Seq(
          "x-request-id"  -> stringSchema,
          "X-Custom"      -> stringSchema,
          "Accept"        -> stringSchema,
          "Content-Type"  -> stringSchema,
          "Authorization" -> stringSchema
        )
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call1, call2))

      val params = openAPI.getPaths.get("/search").getGet.getParameters.asScala.filter(_.getIn == "header").map(_.getName).toList
      params should contain("X-Custom")
      params.count(_.toLowerCase == "x-request-id") shouldBe 1
      params.map(_.toLowerCase) should not contain "accept"
      params.map(_.toLowerCase) should not contain "content-type"
      params.map(_.toLowerCase) should not contain "authorization"
    }

    it("marks path parameters as required: true even when their schema is optional (OAS 3.x)") {
      val optionalPathParamSchema = stringSchema.copy(required = false)
      val call                    = synthCall(path = "/items/{id}", pathParams = Seq("id" -> optionalPathParamSchema))

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val pathParam =
        openAPI.getPaths.get("/items/{id}").getGet.getParameters.asScala.find(p => p.getIn == "path" && p.getName == "id").get
      pathParam.getRequired shouldBe true
    }

    it("is deterministic regardless of input call order") {
      val a = synthCall(queryParams = Seq("alpha" -> stringSchema))
      val b = synthCall(queryParams = Seq("bravo" -> stringSchema))
      val c = synthCall(queryParams = Seq("charlie" -> stringSchema))

      val outputs = Seq(Seq(a, b, c), Seq(c, a, b), Seq(b, c, a)).map { perm =>
        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, perm)
        openAPI.getPaths.get("/search").getGet.getParameters.asScala.filter(_.getIn == "query").map(_.getName).toList
      }

      outputs.distinct should have size 1
      outputs.head shouldBe List("alpha", "bravo", "charlie")
    }
  }

  private val stringSchema = BaklavaSchemaSerializable(Schema.stringSchema)
  private val intSchema    = BaklavaSchemaSerializable(Schema.intSchema)

  private def synthCall(
      path: String = "/search",
      queryParams: Seq[(String, BaklavaSchemaSerializable)] = Nil,
      pathParams: Seq[(String, BaklavaSchemaSerializable)] = Nil,
      headers: Seq[(String, BaklavaSchemaSerializable)] = Nil
  ): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = path,
        path = path,
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
        headersSeq = headers.map { case (name, schema) => BaklavaHeaderSerializable(name, None, schema) },
        pathParametersSeq = pathParams.map { case (name, schema) => BaklavaPathParamSerializable(name, None, schema) },
        queryParametersSeq = queryParams.map { case (name, schema) => BaklavaQueryParamSerializable(name, None, schema) },
        responseDescription = Some("ok"),
        responseHeaders = Nil
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(200),
        headers = Seq.empty,
        bodyString = "",
        requestContentType = None,
        responseContentType = None,
        bodySchema = None
      )
    )
}
