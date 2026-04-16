package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*

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
        method = Some(BaklavaHttpMethod(method)),
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
        status = BaklavaHttpStatus(status),
        headers = BaklavaHttpHeaders(Map.empty),
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
  }
}
