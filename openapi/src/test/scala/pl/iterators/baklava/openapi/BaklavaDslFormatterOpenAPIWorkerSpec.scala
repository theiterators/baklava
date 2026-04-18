package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Header => SttpHeader, Method, StatusCode}

import scala.jdk.CollectionConverters.*

class BaklavaDslFormatterOpenAPIWorkerSpec extends AnyFunSpec with Matchers {

  private val bearerScheme = BaklavaSecuritySchemaSerializable(
    name = "bearerAuth",
    security = BaklavaSecuritySerializable(httpBearer = Some(HttpBearer(bearerFormat = "JWT")))
  )

  private def call(
      method: String,
      path: String,
      summary: Option[String],
      description: Option[String],
      tags: Seq[String],
      operationId: Option[String],
      schemes: Seq[BaklavaSecuritySchemaSerializable],
      status: Int = 200
  ): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = path,
        path = path,
        pathDescription = None,
        pathSummary = None,
        method = Some(Method(method)),
        operationDescription = description,
        operationSummary = summary,
        operationId = operationId,
        operationTags = tags,
        securitySchemes = schemes,
        bodySchema = None,
        headersSeq = Seq.empty,
        pathParametersSeq = Seq.empty,
        queryParametersSeq = Seq.empty,
        responseDescription = Some("ok"),
        responseHeaders = Seq.empty
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(status),
        headers = Seq.empty,
        requestBodyString = "",
        responseBodyString = "",
        requestContentType = None,
        responseContentType = None,
        bodySchema = None
      )
    )

  describe("BaklavaDslFormatterOpenAPIWorker") {
    describe("when merging multiple supports for the same path+method") {
      it("combines security schemes and adds an empty requirement for the unauthenticated variant") {
        val calls = Seq(
          call(
            method = "GET",
            path = "/v1/health-check",
            summary = Some("Returns basic app info"),
            description = Some("Health check (unauthenticated)"),
            tags = Seq("System"),
            operationId = Some("healthCheckPublic"),
            schemes = Seq.empty
          ),
          call(
            method = "GET",
            path = "/v1/health-check",
            summary = Some("Returns app info with DB status"),
            description = Some("Health check (authenticated)"),
            tags = Seq("System", "Auth"),
            operationId = Some("healthCheckAuthenticated"),
            schemes = Seq(bearerScheme)
          )
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, calls)

        val operation = openAPI.getPaths.get("/v1/health-check").getGet
        val security  = operation.getSecurity.asScala.toList

        security should have size 2
        security.head.asScala shouldBe empty
        security(1).asScala.keySet should contain("bearerAuth")

        operation.getDescription should include("Health check (unauthenticated)")
        operation.getDescription should include("Health check (authenticated)")
        operation.getSummary should include("Returns basic app info")
        operation.getSummary should include("Returns app info with DB status")
        operation.getTags.asScala should contain theSameElementsAs Seq("System", "Auth")

        // When variants disagree on operationId, omit it rather than pick one arbitrarily.
        operation.getOperationId shouldBe null
      }

      it("keeps the operationId when every variant agrees") {
        val calls = Seq(
          call("GET", "/v1/agree", Some("a"), Some("d1"), Seq("X"), Some("agreed"), Seq.empty),
          call("GET", "/v1/agree", Some("b"), Some("d2"), Seq("X"), Some("agreed"), Seq(bearerScheme))
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, calls)

        openAPI.getPaths.get("/v1/agree").getGet.getOperationId shouldBe "agreed"
      }

      it("does not add an empty security requirement when every variant uses the same scheme") {
        val calls = Seq(
          call("GET", "/v1/me", Some("Me v1"), Some("desc 1"), Seq("User"), Some("meV1"), Seq(bearerScheme)),
          call("GET", "/v1/me", Some("Me v2"), Some("desc 2"), Seq("User"), Some("meV2"), Seq(bearerScheme))
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, calls)

        val operation = openAPI.getPaths.get("/v1/me").getGet
        val security  = operation.getSecurity.asScala.toList

        security should have size 1
        security.head.asScala.keySet should contain("bearerAuth")
      }

      it("emits an empty security list for a purely unauthenticated endpoint") {
        val calls = Seq(
          call("GET", "/v1/public", Some("Public"), Some("Public endpoint"), Seq.empty, Some("public"), Seq.empty)
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, calls)

        val operation = openAPI.getPaths.get("/v1/public").getGet
        operation.getSecurity.asScala shouldBe empty
      }
    }

    describe("when a call has no HTTP method") {
      it("does not crash; simply skips the operation wiring") {
        val methodless = call("GET", "/v1/skip", None, None, Nil, None, Nil).copy(
          request = call("GET", "/v1/skip", None, None, Nil, None, Nil).request.copy(method = None)
        )

        val openAPI = new OpenAPI()
        noException should be thrownBy BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(methodless))

        val path = Option(openAPI.getPaths).flatMap(ps => Option(ps.get("/v1/skip")))
        path.flatMap(p => Option(p.getGet)) shouldBe None
      }
    }

    describe("response header handling") {
      it("filters Content-Type regardless of case and omits the spurious response header object") {
        val callWithResponseHeader = call("GET", "/v1/ct", None, None, Nil, None, Nil).copy(
          request = call("GET", "/v1/ct", None, None, Nil, None, Nil).request.copy(
            responseHeaders = Seq(
              BaklavaHeaderSerializable("Content-Type", None, BaklavaSchemaSerializable(Schema.stringSchema)),
              BaklavaHeaderSerializable("X-Request-Id", None, BaklavaSchemaSerializable(Schema.stringSchema))
            )
          ),
          response = call("GET", "/v1/ct", None, None, Nil, None, Nil).response.copy(
            headers = Seq(sttp.model.Header("x-request-id", "req-42"))
          )
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(callWithResponseHeader))

        val headers = openAPI.getPaths.get("/v1/ct").getGet.getResponses.get("200").getHeaders
        headers.keySet.asScala should contain only "X-Request-Id"
        headers.get("X-Request-Id").getExample shouldBe "req-42"
      }

      it("does not crash when the declared response header is missing from the head call's response headers") {
        val c = call("GET", "/v1/miss", None, None, Nil, None, Nil).copy(
          request = call("GET", "/v1/miss", None, None, Nil, None, Nil).request.copy(
            responseHeaders = Seq(BaklavaHeaderSerializable("X-Missing", None, BaklavaSchemaSerializable(Schema.stringSchema)))
          )
          // response.headers remains empty — no actual X-Missing in the captured transaction
        )

        val openAPI = new OpenAPI()
        noException should be thrownBy BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(c))
      }

      it("merges distinct response headers across calls and sorts alphabetically") {
        val c1 = call("GET", "/v1/sort", None, None, Nil, None, Nil).copy(
          request = call("GET", "/v1/sort", None, None, Nil, None, Nil).request.copy(
            responseDescription = Some("a"),
            responseHeaders = Seq(BaklavaHeaderSerializable("Zeta", None, BaklavaSchemaSerializable(Schema.stringSchema)))
          )
        )
        val c2 = call("GET", "/v1/sort", None, None, Nil, None, Nil).copy(
          request = call("GET", "/v1/sort", None, None, Nil, None, Nil).request.copy(
            responseDescription = Some("b"),
            responseHeaders = Seq(BaklavaHeaderSerializable("Alpha", None, BaklavaSchemaSerializable(Schema.stringSchema)))
          )
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(c1, c2))

        val headers = openAPI.getPaths.get("/v1/sort").getGet.getResponses.get("200").getHeaders
        headers.keySet.asScala.toList shouldBe List("Alpha", "Zeta")
      }
    }

    describe("components merging") {
      it("preserves user-supplied components (e.g. pre-parsed from openapi-info)") {
        val openAPI       = new OpenAPI()
        val userComponent = new io.swagger.v3.oas.models.Components()
        val userSchema    = new io.swagger.v3.oas.models.media.Schema[AnyRef]()
        userSchema.setType("string")
        userComponent.addSchemas("UserProvidedSchema", userSchema)
        openAPI.components(userComponent)

        val c = call("GET", "/v1/merge", Some("m"), Some("d"), Nil, Some("m"), Seq(bearerScheme))
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(c))

        openAPI.getComponents.getSchemas.keySet.asScala should contain("UserProvidedSchema")
        openAPI.getComponents.getSecuritySchemes.keySet.asScala should contain("bearerAuth")
      }

      it("respects a user-supplied securityScheme under the same name without overwriting") {
        val openAPI       = new OpenAPI()
        val userComponent = new io.swagger.v3.oas.models.Components()
        val userAuth      = new io.swagger.v3.oas.models.security.SecurityScheme()
        userAuth.setDescription("user-supplied")
        userComponent.addSecuritySchemes("bearerAuth", userAuth)
        openAPI.components(userComponent)

        val c = call("GET", "/v1/dup", Some("m"), Some("d"), Nil, Some("m"), Seq(bearerScheme))
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(c))

        openAPI.getComponents.getSecuritySchemes.get("bearerAuth").getDescription shouldBe "user-supplied"
      }
    }

    describe("example key deduplication") {
      it("disambiguates duplicate response-example keys with numeric suffixes") {
        val sameDescription = "Some response"
        val c1              = call("GET", "/v1/dup", None, None, Nil, None, Nil).copy(
          request = call("GET", "/v1/dup", None, None, Nil, None, Nil).request.copy(responseDescription = Some(sameDescription)),
          response = call("GET", "/v1/dup", None, None, Nil, None, Nil).response.copy(
            responseContentType = Some("application/json"),
            responseBodyString = """{"a":1}""",
            bodySchema = Some(BaklavaSchemaSerializable(Schema.stringSchema))
          )
        )
        val c2 = c1.copy(
          response = c1.response.copy(responseBodyString = """{"b":2}""")
        )

        val openAPI = new OpenAPI()
        BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(c1, c2))

        val examples = openAPI.getPaths.get("/v1/dup").getGet.getResponses.get("200").getContent.get("application/json").getExamples
        examples.size shouldBe 2
        examples.keySet.asScala should contain allOf (sameDescription, s"$sameDescription (2)")
      }
    }
  }
}
