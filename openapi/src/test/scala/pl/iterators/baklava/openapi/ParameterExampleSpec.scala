package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*

import scala.jdk.CollectionConverters.*

/** Regression suite for #68: query/path/header parameter example values flow from captured test-case inputs into the generated OpenAPI
  * `parameter.example` / `parameter.examples` fields.
  */
class ParameterExampleSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI parameter example emission (regression for #68)") {

    it("emits a singular example for a path parameter when all calls captured the same value") {
      val call1 = synthCall(
        symbolicPath = "/items/{id}",
        resolvedPath = "/items/42",
        pathParams = Seq(("id", "42"))
      )
      val call2 = synthCall(
        symbolicPath = "/items/{id}",
        resolvedPath = "/items/42",
        pathParams = Seq(("id", "42"))
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call1, call2))

      val idParam = openAPI.getPaths
        .get("/items/{id}")
        .getGet
        .getParameters
        .asScala
        .find(p => p.getName == "id" && p.getIn == "path")
        .getOrElse(fail("id parameter missing"))
      idParam.getExample shouldBe "42"
      Option(idParam.getExamples) shouldBe None
    }

    it("emits named examples (keyed by responseDescription) when calls captured different values") {
      val call1 = synthCall(
        symbolicPath = "/items/{id}",
        resolvedPath = "/items/42",
        pathParams = Seq(("id", "42")),
        scenarioName = Some("found")
      )
      val call2 = synthCall(
        symbolicPath = "/items/{id}",
        resolvedPath = "/items/zzz",
        pathParams = Seq(("id", "zzz")),
        scenarioName = Some("not-found")
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call1, call2))

      val idParam = openAPI.getPaths
        .get("/items/{id}")
        .getGet
        .getParameters
        .asScala
        .find(p => p.getName == "id" && p.getIn == "path")
        .getOrElse(fail("id parameter missing"))
      Option(idParam.getExample) shouldBe None
      val examples = idParam.getExamples.asScala
      examples.keySet should contain theSameElementsAs Set("found", "not-found")
      examples("found").getValue shouldBe "42"
      examples("not-found").getValue shouldBe "zzz"
    }

    it("emits query parameter examples parsed from the resolved URL") {
      val call1 = synthCall(
        symbolicPath = "/search",
        resolvedPath = "/search?q=hello&limit=10",
        queryParams = Seq(("q", ""), ("limit", ""))
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call1))

      val params = openAPI.getPaths.get("/search").getGet.getParameters.asScala.filter(_.getIn == "query")
      params.find(_.getName == "q").get.getExample shouldBe "hello"
      params.find(_.getName == "limit").get.getExample shouldBe "10"
    }

    it("emits header parameter examples from the captured headers map (case-insensitive)") {
      val call = synthCall(
        symbolicPath = "/h",
        resolvedPath = "/h",
        headers = Seq(("X-Request-Id", "req-42")),
        sentHeaders = Map("x-request-id" -> "req-42")
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val headerParam = openAPI.getPaths
        .get("/h")
        .getGet
        .getParameters
        .asScala
        .find(p => p.getName == "X-Request-Id" && p.getIn == "header")
        .getOrElse(fail("header param missing"))
      headerParam.getExample shouldBe "req-42"
    }

    it("URL-decodes path parameter values so %20 round-trips to space") {
      val call = synthCall(
        symbolicPath = "/users/{name}",
        resolvedPath = "/users/John%20Doe",
        pathParams = Seq(("name", "ignored-at-test-level-actual-value-comes-from-URL"))
      )

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val nameParam = openAPI.getPaths
        .get("/users/{name}")
        .getGet
        .getParameters
        .asScala
        .find(p => p.getName == "name" && p.getIn == "path")
        .getOrElse(fail("name parameter missing"))
      nameParam.getExample shouldBe "John Doe"
    }
  }

  describe("end-to-end extraction through BaklavaRequestContextSerializable.apply") {
    // These tests drive the real serializer — no duplicated extraction logic — to catch regressions
    // in the URL parsing that the synthCall-based tests above can't see.

    it("extracts path and query examples from a real resolved URL") {
      val ctx = buildRequestContext(
        symbolicPath = "/users/{id}",
        resolvedPath = "/users/42?limit=10&offset=20",
        pathParamNames = Seq("id"),
        queryParamNames = Seq("limit", "offset")
      )
      val serialized = BaklavaRequestContextSerializable(ctx)

      serialized.pathParametersSeq.find(_.name == "id").flatMap(_.example) shouldBe Some("42")
      serialized.queryParametersSeq.find(_.name == "limit").flatMap(_.example) shouldBe Some("10")
      serialized.queryParametersSeq.find(_.name == "offset").flatMap(_.example) shouldBe Some("20")
    }

    it("strips URL fragments before extracting path params (regression for #72 review)") {
      val ctx = buildRequestContext(
        symbolicPath = "/users/{id}",
        resolvedPath = "/users/42#section-3",
        pathParamNames = Seq("id")
      )
      val serialized = BaklavaRequestContextSerializable(ctx)

      serialized.pathParametersSeq.find(_.name == "id").flatMap(_.example) shouldBe Some("42")
    }

    it("strips URL fragments before extracting query params (regression for #72 review)") {
      val ctx = buildRequestContext(
        symbolicPath = "/search",
        resolvedPath = "/search?q=1#frag",
        queryParamNames = Seq("q")
      )
      val serialized = BaklavaRequestContextSerializable(ctx)

      serialized.queryParametersSeq.find(_.name == "q").flatMap(_.example) shouldBe Some("1")
    }
  }

  // PathParam/QueryParam have `ToPathParam`/`ToQueryParam` implicits that live in traits, not
  // companion objects, so they aren't found by implicit resolution from a non-mixin test. Supply
  // them explicitly — the actual `unapply` logic is immaterial here, we only need the name to
  // flow through and the URL parser on the serializer side to do its job.
  private implicit val stringPathParam: ToPathParam[String] = new ToPathParam[String] {
    override def apply(s: String): String = s
  }
  private implicit val stringQueryParam: ToQueryParam[String] = new ToQueryParam[String] {
    override def apply(s: String): Seq[String] = Seq(s)
  }

  private def buildRequestContext(
      symbolicPath: String,
      resolvedPath: String,
      pathParamNames: Seq[String] = Nil,
      queryParamNames: Seq[String] = Nil
  ): BaklavaRequestContext[Unit, Unit, Unit, Unit, Unit, Unit, Unit] =
    BaklavaRequestContext[Unit, Unit, Unit, Unit, Unit, Unit, Unit](
      symbolicPath = symbolicPath,
      path = resolvedPath,
      pathDescription = None,
      pathSummary = None,
      method = Some(BaklavaHttpMethod("GET")),
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Nil,
      securitySchemes = Nil,
      body = None,
      bodySchema = None,
      headers = BaklavaHttpHeaders(Map.empty),
      headersDefinition = (),
      headersProvided = (),
      headersSeq = Nil,
      security = AppliedSecurity(NoopSecurity, Map.empty),
      pathParameters = (),
      pathParametersProvided = (),
      pathParametersSeq = pathParamNames.map(n => PathParam[String](n, None)),
      queryParameters = (),
      queryParametersProvided = (),
      queryParametersSeq = queryParamNames.map(n => QueryParam[String](n, None)),
      responseDescription = None,
      responseHeaders = Nil
    )

  private val stringSchema = BaklavaSchemaSerializable(Schema.stringSchema)

  private def synthCall(
      symbolicPath: String,
      resolvedPath: String,
      queryParams: Seq[(String, String)] = Nil,
      pathParams: Seq[(String, String)] = Nil,
      headers: Seq[(String, String)] = Nil,
      sentHeaders: Map[String, String] = Map.empty,
      scenarioName: Option[String] = None
  ): BaklavaSerializableCall = {
    // The test directly constructs BaklavaSerializableCall bypassing the normal extraction path,
    // so we can exercise the generator-side emission independently. We still manually plug in
    // example values matching what the real extractor would produce, where relevant.
    val path  = BaklavaPathParamValues(symbolicPath, resolvedPath)
    val query = BaklavaQueryParamValues(resolvedPath)
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = symbolicPath,
        path = resolvedPath,
        pathDescription = None,
        pathSummary = None,
        method = Some(BaklavaHttpMethod("GET")),
        operationDescription = None,
        operationSummary = None,
        operationId = None,
        operationTags = Nil,
        securitySchemes = Nil,
        bodySchema = None,
        headersSeq = headers.map { case (name, _) =>
          BaklavaHeaderSerializable(
            name,
            None,
            stringSchema,
            sentHeaders.find(_._1.toLowerCase == name.toLowerCase).map(_._2)
          )
        },
        pathParametersSeq = pathParams.map { case (name, _) =>
          BaklavaPathParamSerializable(name, None, stringSchema, path.get(name))
        },
        queryParametersSeq = queryParams.map { case (name, _) =>
          BaklavaQueryParamSerializable(name, None, stringSchema, query.get(name))
        },
        responseDescription = scenarioName.orElse(Some("ok")),
        responseHeaders = Nil
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = BaklavaHttpStatus(200),
        headers = BaklavaHttpHeaders(sentHeaders),
        requestBodyString = "",
        responseBodyString = "",
        requestContentType = None,
        responseContentType = None,
        bodySchema = None
      )
    )
  }

  // Mirror of the private extractors in BaklavaSerialize so the test can construct examples
  // exactly as the runtime does, without relying on the full serialize round-trip.
  private object BaklavaPathParamValues {
    def apply(symbolicPath: String, resolvedPath: String): Map[String, String] = {
      val pathOnly      = resolvedPath.split('?').headOption.getOrElse(resolvedPath)
      val templateParts = symbolicPath.split('/')
      val resolvedParts = pathOnly.split('/')
      if (templateParts.length != resolvedParts.length) Map.empty
      else
        templateParts
          .zip(resolvedParts)
          .collect {
            case (t, r) if t.startsWith("{") && t.endsWith("}") =>
              t.substring(1, t.length - 1) -> java.net.URLDecoder.decode(r, "UTF-8")
          }
          .toMap
    }
  }

  private object BaklavaQueryParamValues {
    def apply(resolvedPath: String): Map[String, String] = {
      val queryPart = resolvedPath.split('?').drop(1).headOption.getOrElse("")
      if (queryPart.isEmpty) Map.empty
      else
        queryPart
          .split('&')
          .filter(_.nonEmpty)
          .toSeq
          .map { kv =>
            val eq = kv.indexOf('=')
            if (eq < 0) java.net.URLDecoder.decode(kv, "UTF-8")           -> ""
            else java.net.URLDecoder.decode(kv.substring(0, eq), "UTF-8") -> java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8")
          }
          .groupBy(_._1)
          .view
          .mapValues(_.map(_._2).mkString(","))
          .toMap
    }
  }
}
