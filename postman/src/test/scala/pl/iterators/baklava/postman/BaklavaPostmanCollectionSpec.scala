package pl.iterators.baklava.postman

import io.circe.{Json, Printer}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

class BaklavaPostmanCollectionSpec extends AnyFunSpec with Matchers {

  describe("BaklavaPostmanCollection.build") {

    it("wraps the output in a valid Postman Collection v2.1 envelope") {
      val json = BaklavaPostmanCollection.build("My API", Seq(simpleGetCall()))
      json.hcursor.downField("info").downField("name").as[String].toOption shouldBe Some("My API")
      json.hcursor.downField("info").downField("schema").as[String].toOption shouldBe
      Some("https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
    }

    it("groups calls by first operationTag into folders; untagged ones go top-level") {
      val json = BaklavaPostmanCollection.build(
        "API",
        Seq(
          taggedCall(tag = "Users", method = "GET", path = "/users"),
          taggedCall(tag = "Users", method = "POST", path = "/users"),
          simpleGetCall() // no tags
        )
      )

      val topLevelNames = json.hcursor.downField("item").values.get.map(_.hcursor.downField("name").as[String].toOption.get).toSeq
      topLevelNames should contain("Users")
      topLevelNames should not contain "API" // folder name, not the call's

      val usersFolder = json.hcursor
        .downField("item")
        .values
        .get
        .find(_.hcursor.downField("name").as[String].toOption.contains("Users"))
        .get
      usersFolder.hcursor.downField("item").values.get should have size 2
    }

    it("rewrites OpenAPI-style `{name}` path segments as Postman's `:name` and emits variables") {
      val call = callWithPath("/users/{userId}/items/{itemId}", pathParams = Seq("userId" -> "42", "itemId" -> "7"))
      val json = BaklavaPostmanCollection.build("API", Seq(call))

      val url      = json.hcursor.downField("item").downArray.downField("request").downField("url")
      val rawUrl   = url.downField("raw").as[String].toOption.get
      val segments = url.downField("path").as[List[String]].toOption.get

      rawUrl shouldBe "{{baseUrl}}/users/:userId/items/:itemId"
      segments shouldBe List("users", ":userId", "items", ":itemId")

      val variables = url.downField("variable").values.get.toList
      variables.map(_.hcursor.downField("key").as[String].toOption.get) should contain theSameElementsAs Seq("userId", "itemId")
    }

    it("emits a body block with content-type-derived language when the request had a body") {
      val call = simpleGetCall()
        .copy(
          request = simpleGetCall().request.copy(bodyString = """{"name":"alice"}"""),
          response = simpleGetCall().response.copy(requestContentType = Some("application/json"))
        )

      val body = BaklavaPostmanCollection
        .build("API", Seq(call))
        .hcursor
        .downField("item")
        .downArray
        .downField("request")
        .downField("body")

      body.downField("mode").as[String].toOption shouldBe Some("raw")
      body.downField("raw").as[String].toOption shouldBe Some("""{"name":"alice"}""")
      body.downField("options").downField("raw").downField("language").as[String].toOption shouldBe Some("json")
    }

    it("strips Authorization / Content-Type from header[] (Postman models them separately)") {
      val call = simpleGetCall().copy(
        request = simpleGetCall().request.copy(
          headersSeq = Seq(
            BaklavaHeaderSerializable("Authorization", None, stringSchema, Some("Bearer leaked")),
            BaklavaHeaderSerializable("content-type", None, stringSchema, Some("application/json")),
            BaklavaHeaderSerializable("X-Request-Id", None, stringSchema, Some("req-1"))
          )
        )
      )

      val headers = BaklavaPostmanCollection
        .build("API", Seq(call))
        .hcursor
        .downField("item")
        .downArray
        .downField("request")
        .downField("header")
        .values
        .get
        .toList

      val keys = headers.map(_.hcursor.downField("key").as[String].toOption.get)
      keys should contain("X-Request-Id")
      keys should not contain "Authorization"
      keys.map(_.toLowerCase) should not contain "content-type"
    }

    it("maps HttpBearer scheme to a Postman bearer auth block with a collection variable placeholder") {
      val scheme = BaklavaSecuritySchemaSerializable(
        "bearerAuth",
        BaklavaSecuritySerializable(httpBearer = Some(HttpBearer()))
      )
      val call = simpleGetCall().copy(request = simpleGetCall().request.copy(securitySchemes = Seq(scheme)))
      val json = BaklavaPostmanCollection.build("API", Seq(call))

      val auth = json.hcursor.downField("item").downArray.downField("request").downField("auth")
      auth.downField("type").as[String].toOption shouldBe Some("bearer")

      val firstBearer = auth.downField("bearer").downArray
      firstBearer.downField("key").as[String].toOption shouldBe Some("token")
      firstBearer.downField("value").as[String].toOption shouldBe Some("{{bearerAuthToken}}")
    }

    it("declares collection variables for each security scheme's credentials plus {{baseUrl}}") {
      val bearer = BaklavaSecuritySchemaSerializable("bearerAuth", BaklavaSecuritySerializable(httpBearer = Some(HttpBearer())))
      val basic  = BaklavaSecuritySchemaSerializable("basicAuth", BaklavaSecuritySerializable(httpBasic = Some(HttpBasic())))
      val apiKey =
        BaklavaSecuritySchemaSerializable("apiKey", BaklavaSecuritySerializable(apiKeyInHeader = Some(ApiKeyInHeader("X-API-Key"))))

      val call = simpleGetCall().copy(request = simpleGetCall().request.copy(securitySchemes = Seq(bearer, basic, apiKey)))
      val json = BaklavaPostmanCollection.build("API", Seq(call))

      val vars = json.hcursor.downField("variable").values.get.map(_.hcursor.downField("key").as[String].toOption.get).toSeq
      vars should contain allOf (
        "baseUrl",
        "bearerAuthToken",
        "basicAuthUsername",
        "basicAuthPassword",
        "apiKeyValue"
      )
    }

    it("URL-encodes query keys and values in the `raw` URL") {
      val call = simpleGetCall().copy(
        request = simpleGetCall().request.copy(
          queryParametersSeq = Seq(
            BaklavaQueryParamSerializable("q name", None, stringSchema, Some("hello world & friends"))
          )
        )
      )
      val raw = BaklavaPostmanCollection
        .build("API", Seq(call))
        .hcursor
        .downField("item")
        .downArray
        .downField("request")
        .downField("url")
        .downField("raw")
        .as[String]
        .toOption
        .get

      raw should include("q+name=hello+world+%26+friends")
      raw should not include " "
    }

    it("normalizes content-type (case + parameters) when inferring body language") {
      val call = simpleGetCall().copy(
        request = simpleGetCall().request.copy(bodyString = """{"x":1}"""),
        response = simpleGetCall().response.copy(requestContentType = Some("Application/JSON; charset=utf-8"))
      )
      val lang = BaklavaPostmanCollection
        .build("API", Seq(call))
        .hcursor
        .downField("item")
        .downArray
        .downField("request")
        .downField("body")
        .downField("options")
        .downField("raw")
        .downField("language")
        .as[String]
        .toOption
      lang shouldBe Some("json")
    }

    it("normalizes response content-type when inferring `_postman_previewlanguage`") {
      val call = simpleGetCall().copy(
        response = simpleGetCall().response.copy(responseContentType = Some("Application/JSON; charset=utf-8"))
      )
      val lang = BaklavaPostmanCollection
        .build("API", Seq(call))
        .hcursor
        .downField("item")
        .downArray
        .downField("response")
        .downArray
        .downField("_postman_previewlanguage")
        .as[String]
        .toOption
      lang shouldBe Some("json")
    }

    it("uses the same default HTTP method when grouping and rendering") {
      val methodless = simpleGetCall().copy(request = simpleGetCall().request.copy(method = None))
      val method     = BaklavaPostmanCollection
        .build("API", Seq(methodless))
        .hcursor
        .downField("item")
        .downArray
        .downField("request")
        .downField("method")
        .as[String]
        .toOption
      method shouldBe Some("GET")
    }

    it("does not emit a Postman oauth2 auth block for `*InCookie` schemes") {
      val flows  = OAuthFlows()
      val cookie = BaklavaSecuritySchemaSerializable(
        "sessionOAuth",
        BaklavaSecuritySerializable(oAuth2InCookie = Some(OAuth2InCookie(flows)))
      )
      val call = simpleGetCall().copy(request = simpleGetCall().request.copy(securitySchemes = Seq(cookie)))
      val json = BaklavaPostmanCollection.build("API", Seq(call))

      val auth = json.hcursor.downField("item").downArray.downField("request").downField("auth")
      auth.as[Json].toOption shouldBe Some(Json.Null)

      val vars = json.hcursor.downField("variable").values.get.map(_.hcursor.downField("key").as[String].toOption.get).toSeq
      vars should contain("baseUrl")
      vars should not contain "sessionOAuthToken"
    }

    it("omits collection variables for security schemes without a Postman auth equivalent (mutualTls)") {
      val mtls = BaklavaSecuritySchemaSerializable("mtls", BaklavaSecuritySerializable(mutualTls = Some(MutualTls())))
      val call = simpleGetCall().copy(request = simpleGetCall().request.copy(securitySchemes = Seq(mtls)))
      val json = BaklavaPostmanCollection.build("API", Seq(call))

      val vars = json.hcursor.downField("variable").values.get.map(_.hcursor.downField("key").as[String].toOption.get).toSeq
      vars should contain("baseUrl")
      vars should not contain "mtlsToken"
      vars should not contain "mtlsValue"

      val auth = json.hcursor.downField("item").downArray.downField("request").downField("auth")
      auth.as[Json].toOption shouldBe Some(Json.Null)
    }

    it("produces a document that satisfies the Postman v2.1 structural invariants after printing") {
      val bearer = BaklavaSecuritySchemaSerializable("bearerAuth", BaklavaSecuritySerializable(httpBearer = Some(HttpBearer())))
      val apiKey = BaklavaSecuritySchemaSerializable(
        "apiKey",
        BaklavaSecuritySerializable(apiKeyInQuery = Some(ApiKeyInQuery("token")))
      )

      val calls = Seq(
        taggedCall("Users", "GET", "/users"),
        callWithPath("/users/{userId}", Seq("userId" -> "42")).copy(
          request = callWithPath("/users/{userId}", Seq("userId" -> "42")).request.copy(
            operationTags = Seq("Users"),
            securitySchemes = Seq(bearer)
          )
        ),
        simpleGetCall().copy(
          request = simpleGetCall().request.copy(
            bodyString = """{"name":"alice"}""",
            securitySchemes = Seq(apiKey)
          ),
          response = simpleGetCall().response.copy(requestContentType = Some("application/json"))
        )
      )

      val json    = BaklavaPostmanCollection.build("API", calls)
      val printed = Printer.spaces2.copy(dropNullValues = true).print(json)
      val parsed  = io.circe.parser.parse(printed).toOption.get

      assertPostmanStructure(parsed)
    }

    it("emits one `response` example per captured call at the endpoint") {
      val a = simpleGetCall().copy(
        request = simpleGetCall().request.copy(responseDescription = Some("First example")),
        response = simpleGetCall().response.copy(status = StatusCode(200), bodyString = """{"id":1}""")
      )
      val b = simpleGetCall().copy(
        request = simpleGetCall().request.copy(responseDescription = Some("Not found")),
        response = simpleGetCall().response.copy(status = StatusCode(404), bodyString = """{"error":"missing"}""")
      )

      val responses = BaklavaPostmanCollection
        .build("API", Seq(a, b))
        .hcursor
        .downField("item")
        .downArray
        .downField("response")
        .values
        .get
        .toSeq

      responses should have size 2
      val names = responses.map(_.hcursor.downField("name").as[String].toOption.get)
      names should contain allOf ("First example", "Not found")
    }
  }

  /** Minimal Postman v2.1 schema conformance: what the actual Postman importer requires. Catches regressions that break import without
    * pulling in a full JSON-schema validator.
    */
  private def assertPostmanStructure(doc: Json): Unit = {
    val info = doc.hcursor.downField("info")
    info.downField("name").as[String].toOption shouldBe defined
    info.downField("schema").as[String].toOption.get should include("collection")

    val items = doc.hcursor.downField("item").values.getOrElse(fail("top-level `item` must be an array"))
    items.foreach(assertItem)

    assertNoNulls(doc, path = "")

    doc.hcursor
      .downField("variable")
      .values
      .foreach(_.foreach { v =>
        v.hcursor.downField("key").as[String].toOption shouldBe defined
        v.hcursor.downField("value").as[String].toOption shouldBe defined
      })
  }

  private def assertItem(item: Json): Unit = {
    item.hcursor.downField("name").as[String].toOption shouldBe defined

    val hasRequest = item.hcursor.downField("request").succeeded
    val hasItem    = item.hcursor.downField("item").succeeded
    assert(hasRequest ^ hasItem, s"each item must be a folder (item[]) XOR an endpoint (request): ${item.noSpaces}")

    if (hasItem) {
      item.hcursor.downField("item").values.get.foreach(assertItem)
    } else {
      val req = item.hcursor.downField("request")
      req.downField("method").as[String].toOption.get should not be empty

      val url  = req.downField("url")
      val host = url.downField("host").values.get
      host should not be empty
      host.foreach(_.isString shouldBe true)

      url.downField("path").values.get.foreach(_.isString shouldBe true)

      item.hcursor
        .downField("response")
        .values
        .foreach(_.foreach { r =>
          r.hcursor.downField("name").as[String].toOption shouldBe defined
          r.hcursor.downField("code").as[Int].toOption shouldBe defined
        })
    }
  }

  private def assertNoNulls(j: Json, path: String): Unit =
    j.fold(
      jsonNull = fail(s"null leaf at $path — Postman v2.1 rejects nulls; the printer should have stripped it via dropNullValues"),
      jsonBoolean = _ => (),
      jsonNumber = _ => (),
      jsonString = _ => (),
      jsonArray = _.zipWithIndex.foreach { case (v, i) => assertNoNulls(v, s"$path[$i]") },
      jsonObject = _.toIterable.foreach { case (k, v) => assertNoNulls(v, s"$path.$k") }
    )

  // Test-data helpers below — kept terse since they're purely mechanical construction of the
  // serializable types and don't carry their own invariants worth commenting.

  private val stringSchema = BaklavaSchemaSerializable(Schema.stringSchema)

  private def simpleGetCall(): BaklavaSerializableCall =
    call(method = "GET", path = "/users")

  private def taggedCall(tag: String, method: String, path: String): BaklavaSerializableCall =
    call(method = method, path = path, tags = Seq(tag))

  private def callWithPath(path: String, pathParams: Seq[(String, String)]): BaklavaSerializableCall =
    call(
      method = "GET",
      path = path,
      pathParams = pathParams.map { case (n, v) => BaklavaPathParamSerializable(n, None, stringSchema, Some(v)) }
    )

  private def call(
      method: String,
      path: String,
      tags: Seq[String] = Nil,
      pathParams: Seq[BaklavaPathParamSerializable] = Nil
  ): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = path,
        path = path,
        pathDescription = None,
        pathSummary = None,
        method = Some(Method(method)),
        operationDescription = None,
        operationSummary = None,
        operationId = None,
        operationTags = tags,
        securitySchemes = Nil,
        bodySchema = None,
        bodyString = "",
        headersSeq = Nil,
        pathParametersSeq = pathParams,
        queryParametersSeq = Nil,
        responseDescription = None,
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
