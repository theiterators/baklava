package pl.iterators.baklava.postman

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
