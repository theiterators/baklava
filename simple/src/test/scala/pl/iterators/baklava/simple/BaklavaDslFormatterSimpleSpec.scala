package pl.iterators.baklava.simple

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*

class BaklavaDslFormatterSimpleSpec extends AnyFunSpec with Matchers {

  private val generator = new BaklavaDslFormatterSimple

  describe("generateEndpointPage") {

    it("fails loudly with a diagnostic when called on an empty Seq") {
      val ex = intercept[IllegalArgumentException](generator.generateEndpointPage(Seq.empty))
      ex.getMessage should include("generateEndpointPage")
    }

    it("renders each call's distinct request body — not just the first call's") {
      val html = generator.generateEndpointPage(
        Seq(
          jsonCall(status = 200, desc = "Alice", requestBody = """{"name":"Alice"}""", responseBody = """{"id":1}"""),
          jsonCall(status = 200, desc = "Bob", requestBody = """{"name":"Bob"}""", responseBody = """{"id":2}""")
        )
      )

      html should include("Alice")
      html should include("Bob")
    }
  }

  private def jsonCall(status: Int, desc: String, requestBody: String, responseBody: String): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = "/users",
        path = "/users",
        pathDescription = None,
        pathSummary = None,
        method = Some(BaklavaHttpMethod("POST")),
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
        requestBodyString = requestBody,
        responseBodyString = responseBody,
        requestContentType = Some("application/json"),
        responseContentType = Some("application/json"),
        bodySchema = None
      )
    )
}
