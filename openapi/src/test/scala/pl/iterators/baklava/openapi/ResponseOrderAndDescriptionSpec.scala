package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Header => SttpHeader, Method, StatusCode}

import scala.jdk.CollectionConverters.*

class ResponseOrderAndDescriptionSpec extends AnyFunSpec with Matchers {

  describe("BaklavaDslFormatterOpenAPIWorker response rendering") {

    it("orders examples alphabetically by description regardless of input order (regression for #51)") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(
          jsonCall("Return users matching 'jane'", """[{"id":1}]"""),
          jsonCall("Return all users", """[{"id":1},{"id":2}]"""),
          jsonCall("Return first page with 2 users", """[{"id":1},{"id":2}]""")
        )
      )

      val examples = openAPI.getPaths.get("/users").getGet.getResponses.get("200").getContent.get("application/json").getExamples
      examples.asScala.keys.toList shouldBe List(
        "Return all users",
        "Return first page with 2 users",
        "Return users matching 'jane'"
      )
    }

    it("produces the same example order regardless of input permutation (regression for #51)") {
      val a = jsonCall("Return users matching 'jane'", "[]")
      val b = jsonCall("Return all users", "[]")
      val c = jsonCall("Return first page with 2 users", "[]")

      val permutations = Seq(Seq(a, b, c), Seq(c, a, b), Seq(b, c, a), Seq(a, c, b))
      val keyLists     = permutations.map { perm =>
        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, perm)
        openAPI.getPaths.get("/users").getGet.getResponses.get("200").getContent.get("application/json").getExamples.asScala.keys.toList
      }

      keyLists.distinct should have size 1
    }

    it("merges distinct response descriptions, sorted, rather than picking the first example's description (regression for #50)") {
      val calls = Seq(
        jsonCall("Return users matching 'jane'", "[]"),
        jsonCall("Return all users", "[]"),
        jsonCall("Return first page with 2 users", "[]")
      )

      // Merged description must be identical regardless of input permutation.
      val descriptions = Seq(calls, calls.reverse, Seq(calls(1), calls(2), calls.head)).map { perm =>
        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, perm)
        openAPI.getPaths.get("/users").getGet.getResponses.get("200").getDescription
      }

      descriptions.distinct should have size 1
      descriptions.head shouldBe "Return all users / Return first page with 2 users / Return users matching 'jane'"
    }

    it("keeps a single description unchanged when all examples share it") {
      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(
        openAPI,
        Seq(
          jsonCall("OK", "[]"),
          jsonCall("OK", "[]")
        )
      )

      openAPI.getPaths.get("/users").getGet.getResponses.get("200").getDescription shouldBe "OK"
    }
  }

  private def jsonCall(responseDescription: String, body: String): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = "/users",
        path = "/users",
        pathDescription = None,
        pathSummary = None,
        method = Some(Method("GET")),
        operationDescription = None,
        operationSummary = None,
        operationId = None,
        operationTags = Seq.empty,
        securitySchemes = Seq.empty,
        bodySchema = None,
        headersSeq = Seq.empty,
        pathParametersSeq = Seq.empty,
        queryParametersSeq = Seq.empty,
        responseDescription = Some(responseDescription),
        responseHeaders = Seq.empty
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(200),
        headers = Seq.empty,
        requestBodyString = "",
        responseBodyString = body,
        requestContentType = None,
        responseContentType = Some("application/json"),
        bodySchema = Some(BaklavaSchemaSerializable(Schema.stringSchema))
      )
    )
}
