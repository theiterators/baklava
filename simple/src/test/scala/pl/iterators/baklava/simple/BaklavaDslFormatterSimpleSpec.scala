package pl.iterators.baklava.simple

import io.circe.parser
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

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

    it("produces a deterministic top-level `required` list in the JSON Schema regardless of input order") {
      val schemaA = objectSchema(Map("b" -> stringRequired, "a" -> stringRequired, "c" -> stringRequired))
      val schemaB = objectSchema(Map("c" -> stringRequired, "b" -> stringRequired, "a" -> stringRequired))

      def requiredArray(raw: String): Seq[String] =
        parser.parse(raw).toTry.get.hcursor.downField("required").as[List[String]].toTry.get

      val requiredA = requiredArray(generator.jsonSchemaV7(schemaA))
      requiredA shouldBe List("a", "b", "c")
      requiredArray(generator.jsonSchemaV7(schemaB)) shouldBe requiredA
    }

    it("omits the `required` keyword on non-object schemas (valid JSON Schema)") {
      val schema = objectSchema(Map("a" -> stringRequired, "b" -> stringRequired))
      val json   = parser.parse(generator.jsonSchemaV7(schema)).toTry.get.hcursor

      // Object schema itself gets a `required` list.
      json.downField("required").as[List[String]].toTry.get shouldBe List("a", "b")

      // Leaf (string) properties must NOT carry a `required` key — per JSON Schema, `required`
      // is only meaningful on object schemas and lists property names, not an empty array on
      // scalar leaves.
      json.downField("properties").downField("a").downField("required").succeeded shouldBe false
      json.downField("properties").downField("b").downField("required").succeeded shouldBe false
    }

    it("renders captured parameter examples inline when calls agree, as a scenario list when they diverge") {
      val base      = jsonCall(status = 200, desc = "A", requestBody = "", responseBody = "{}")
      val callWithQ = base.copy(
        request = base.request.copy(
          queryParametersSeq = Seq(BaklavaQueryParamSerializable("page", None, stringRequired, Some("1")))
        )
      )
      val singleHtml = generator.generateEndpointPage(Seq(callWithQ))
      singleHtml should include("<code>String</code> = <code>1</code>")
      singleHtml should not include """ul class="examples""""

      val callB = callWithQ.copy(
        request = callWithQ.request.copy(
          responseDescription = Some("B"),
          queryParametersSeq = Seq(BaklavaQueryParamSerializable("page", None, stringRequired, Some("2")))
        )
      )
      val multiHtml = generator.generateEndpointPage(Seq(callWithQ, callB))
      multiHtml should include("""ul class="examples"""")
      multiHtml should include("<em>A:</em> <code>1</code>")
      multiHtml should include("<em>B:</em> <code>2</code>")
    }

    it("renders declared response headers with their captured example values (regression for C7)") {
      val base     = jsonCall(status = 200, desc = "ok", requestBody = "", responseBody = "")
      val withHdrs = base.copy(
        request = base.request.copy(
          responseHeaders = Seq(
            BaklavaHeaderSerializable("X-Rate-Limit", None, stringRequired),
            BaklavaHeaderSerializable("X-Request-Id", None, stringRequired)
          )
        ),
        response = base.response.copy(
          headers = Seq(sttp.model.Header("x-rate-limit", "100"), sttp.model.Header("X-Request-Id", "req-42"))
        )
      )
      val html = generator.generateEndpointPage(Seq(withHdrs))
      html should include("Response headers")
      html should include("X-Rate-Limit")
      html should include("100") // case-insensitive match picked up the lowercase key
      html should include("X-Request-Id")
      html should include("req-42")
    }

    it("HTML-escapes symbolicPath, method, operationId, tags, and other user-supplied values") {
      val hostile = jsonCall(status = 200, desc = "ok", requestBody = "", responseBody = "")
      val evil    = hostile.copy(
        request = hostile.request.copy(
          symbolicPath = "/users/<script>alert(1)</script>",
          operationId = Some("""steal"money"""),
          operationTags = Seq("<img src=x onerror=alert(1)>"),
          operationSummary = Some("<b>bold</b>")
        )
      )
      val html = generator.generateEndpointPage(Seq(evil))

      // The literal attack must appear only in escaped form.
      html should not include "<script>alert"
      html should not include "<img src=x onerror"
      // Escaped forms must be present where the attack was injected.
      html should include("&lt;script&gt;alert(1)&lt;/script&gt;")
      html should include("&lt;img src=x onerror=alert(1)&gt;")
    }
  }

  describe("renderCurl") {
    it("emits an explicit curl command with method, base-URL placeholder, content-type and body") {
      val call = jsonCall(
        status = 201,
        desc = "ok",
        requestBody = """{"name":"Alice"}""",
        responseBody = """{"id":1}"""
      )
      val out = generator.renderCurl(call)

      out should include("curl -X POST")
      out should include("'$BASE_URL/users'")
      out should include("-H 'Content-Type: application/json'")
      out should include("""--data-raw '{"name":"Alice"}'""")
      // Payload is copied from the adjacent <pre>'s textContent (not a data-* attribute), since
      // attribute values have whitespace normalized and we'd lose line-continuation newlines.
      out should include("""<button class="copy-btn" type="button">Copy</button>""")
      out should not include "data-clipboard="
    }

    it("never leaks a declared Authorization/Cookie/API-key header value — only placeholders") {
      val base = jsonCall(status = 200, desc = "ok", requestBody = "", responseBody = "{}")
      val call = base.copy(
        request = base.request.copy(
          securitySchemes = Seq(
            BaklavaSecuritySchemaSerializable(
              "bearerAuth",
              BaklavaSecuritySerializable(httpBearer = Some(HttpBearer(bearerFormat = "JWT")))
            ),
            BaklavaSecuritySchemaSerializable(
              "apiKey",
              BaklavaSecuritySerializable(apiKeyInHeader = Some(ApiKeyInHeader("X-API-Key")))
            )
          ),
          // Headers a naive user might also declare alongside the security scheme — we must NOT
          // leak these captured values (which would contain a real token / cookie / key).
          headersSeq = Seq(
            BaklavaHeaderSerializable("Authorization", None, stringRequired, Some("Bearer real-jwt-xyz")),
            BaklavaHeaderSerializable("Cookie", None, stringRequired, Some("session=real-cookie")),
            BaklavaHeaderSerializable("X-API-Key", None, stringRequired, Some("real-api-key"))
          )
        )
      )
      val out = generator.renderCurl(call)

      out should not include "real-jwt-xyz"
      out should not include "real-cookie"
      out should not include "real-api-key"
      out should include("-H 'Authorization: Bearer &lt;TOKEN&gt;'")
      out should include("-H 'X-API-Key: &lt;API_KEY&gt;'")
    }

    it("emits security-scheme placeholder headers (bearer, api key)") {
      val base = jsonCall(status = 200, desc = "ok", requestBody = "", responseBody = "{}")
      val call = base.copy(
        request = base.request.copy(
          securitySchemes = Seq(
            BaklavaSecuritySchemaSerializable(
              "bearerAuth",
              BaklavaSecuritySerializable(httpBearer = Some(HttpBearer(bearerFormat = "JWT")))
            ),
            BaklavaSecuritySchemaSerializable(
              "apiKey",
              BaklavaSecuritySerializable(apiKeyInHeader = Some(ApiKeyInHeader("X-API-Key")))
            )
          )
        )
      )
      val out = generator.renderCurl(call)

      out should include("-H 'Authorization: Bearer &lt;TOKEN&gt;'")
      out should include("-H 'X-API-Key: &lt;API_KEY&gt;'")
    }

    it("shell-escapes single quotes inside body and path") {
      val base = jsonCall(status = 200, desc = "ok", requestBody = """he said 'hi'""", responseBody = "")
      val out  = generator.renderCurl(base)
      // The embedded apostrophes are closed, escaped, reopened: '\''. `escHtml` doesn't touch
      // apostrophes, so the shell-escape form appears verbatim inside the <pre>.
      out should include("""'he said '\''hi'\'''""")
    }
  }

  describe("renderIndexHtml") {
    it("groups endpoints under each tag section, with untagged operations under 'default'") {
      val rendered: Seq[(String, String, String, Seq[String])] = Seq(
        ("GET", "/a", "GET__a.html", Seq("Users")),
        ("GET", "/b", "GET__b.html", Seq("Users", "Admin")),
        ("GET", "/c", "GET__c.html", Seq.empty)
      )
      val html = generator.renderIndexHtml(rendered)

      val adminIdx   = html.indexOf(">Admin<")
      val usersIdx   = html.indexOf(">Users<")
      val defaultIdx = html.indexOf(">default<")
      adminIdx should be > -1
      usersIdx should be > adminIdx
      defaultIdx should be > usersIdx

      // Endpoint /b appears under BOTH Users and Admin sections.
      val bMatches = html.split("""<span class="path">/b</span>""", -1).length - 1
      bMatches shouldBe 2
    }

    it("HTML-escapes tag names so hostile tag strings can't break out") {
      val html = generator.renderIndexHtml(Seq(("GET", "/x", "GET__x.html", Seq("<script>"))))
      html should include("&lt;script&gt;")
      html should not include "<script>"
    }
  }

  describe("toFilename") {
    it("produces distinct filenames for paths that differ only by `/` vs `_` (regression for C6)") {
      generator.toFilename("GET /a/b") should not be generator.toFilename("GET /a_b")
    }

    it("is deterministic across runs") {
      val same1 = generator.toFilename("GET /users/{id}")
      val same2 = generator.toFilename("GET /users/{id}")
      same1 shouldBe same2
    }
  }

  private val stringRequired: BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "String",
      `type` = SchemaType.StringType,
      format = None,
      properties = Map.empty,
      items = None,
      `enum` = None,
      required = true,
      additionalProperties = false,
      default = None,
      description = None
    )

  private def objectSchema(props: Map[String, BaklavaSchemaSerializable]): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "Obj",
      `type` = SchemaType.ObjectType,
      format = None,
      properties = props,
      items = None,
      `enum` = None,
      required = true,
      additionalProperties = false,
      default = None,
      description = None
    )

  private def jsonCall(status: Int, desc: String, requestBody: String, responseBody: String): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = "/users",
        path = "/users",
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
        responseDescription = Some(desc),
        responseHeaders = Nil
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(status),
        headers = Seq.empty,
        requestBodyString = requestBody,
        responseBodyString = responseBody,
        requestContentType = Some("application/json"),
        responseContentType = Some("application/json"),
        bodySchema = None
      )
    )
}
