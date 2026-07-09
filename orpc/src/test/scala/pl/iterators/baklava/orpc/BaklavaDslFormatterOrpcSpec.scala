package pl.iterators.baklava.orpc

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.{TsNaming, TsPathRouter, TsSchemaRefs, TsZodDialect, TsZodRenderer}
import sttp.model.{Method, StatusCode}

class BaklavaDslFormatterOrpcSpec extends AnyFunSpec with Matchers {

  private val generator   = new BaklavaDslFormatterOrpc
  private val zodRenderer = new TsZodRenderer(TsZodDialect.orpc)

  describe("createContractForEndpoint(): route block") {

    it("emits method, path (oRPC-native {param} syntax), successStatus and detailed input structure") {
      val entry = endpoint(
        "GET",
        "/v1/auctions/{auctionId}",
        call("/v1/auctions/{auctionId}", pathParams = Seq("auctionId" -> uuidSchema(required = true)))
      )
      entry should include("  get: oc")
      entry should include("      method: 'GET'")
      entry should include("      path: '/v1/auctions/{auctionId}'")
      entry should include("      successStatus: 200")
      entry should include("      inputStructure: 'detailed'")
    }

    it("keeps {param} placeholders untouched (no :param conversion)") {
      val entry = endpoint(
        "GET",
        "/v1/users/{user-id}/photo",
        call("/v1/users/{user-id}/photo", pathParams = Seq("user-id" -> uuidSchema(required = true)))
      )
      entry should include("      path: '/v1/users/{user-id}/photo'")
      (entry should not).include(":user-id")
    }

    it("switches to detailed output when several distinct 2xx statuses were captured") {
      val entry = endpoint(
        "POST",
        "/v1/things",
        call("/v1/things", method = "POST", status = 201, responseSchema = Some(objectSchema(Map("id" -> stringSchema())))),
        call("/v1/things", method = "POST", status = 200)
      )
      (entry should not).include("successStatus")
      entry should include("      outputStructure: 'detailed'")
      entry should include("z.object({status: z.literal(200)})")
      entry should include("z.object({status: z.literal(201), body: z.object(")
    }
  }

  describe("createContractForEndpoint(): input groups") {

    it("emits params for path parameters") {
      val entry = endpoint(
        "GET",
        "/v1/auctions/{auctionId}",
        call("/v1/auctions/{auctionId}", pathParams = Seq("auctionId" -> uuidSchema(required = true)))
      )
      entry should include("      params: z.object({auctionId: z.uuid()})")
    }

    it("marks a query group .optional() when every parameter is optional") {
      val entry = endpoint(
        "GET",
        "/v1/auctions",
        call("/v1/auctions", queryParams = Seq("limit" -> intSchema(required = false), "offset" -> intSchema(required = false)))
      )
      entry should include("      query: z.object({limit: z.number().int().nullish(), offset: z.number().int().nullish()}).optional()")
    }

    it("keeps a query group required when any parameter is required") {
      val entry = endpoint(
        "GET",
        "/v1/auctions",
        call("/v1/auctions", queryParams = Seq("limit" -> intSchema(required = true)))
      )
      entry should include("      query: z.object({limit: z.number().int()})")
      (entry should not).include("}).optional()")
    }

    it("quotes kebab-case parameter names") {
      val entry = endpoint(
        "GET",
        "/v1/auctions",
        call("/v1/auctions", queryParams = Seq("after-id" -> uuidSchema(required = false)))
      )
      entry should include(""""after-id": z.uuid().nullish()""")
    }

    it("omits .input entirely when there are no parameters and no body") {
      val entry = endpoint("GET", "/v1/health", call("/v1/health"))
      (entry should not).include(".input(")
    }

    it("emits body from the captured request schema") {
      val entry = endpoint(
        "POST",
        "/v1/things",
        call("/v1/things", method = "POST", status = 201, bodySchema = Some(objectSchema(Map("name" -> stringSchema()))))
      )
      entry should include("      body: z.object(")
      entry should include(""""name": z.string()""")
    }

    it("renders multipart bodies with z.instanceof(File)") {
      val entry = endpoint(
        "POST",
        "/v1/uploads",
        call(
          "/v1/uploads",
          method = "POST",
          status = 201,
          multipartParts = Some(Seq(BaklavaMultipartPartSerializable("file", isFile = true)))
        )
      )
      entry should include("      body: z.object({file: z.file()})")
    }
  }

  describe("createContractForEndpoint(): output") {

    it("emits .output(z.void()) for a bodyless success") {
      val entry = endpoint("DELETE", "/v1/things/{id}", call("/v1/things/{id}", method = "DELETE", status = 204))
      entry should include("      successStatus: 204")
      entry should include(".output(z.void())")
    }

    it("unions distinct success bodies") {
      val entry = endpoint(
        "GET",
        "/v1/mixed",
        call("/v1/mixed", responseSchema = Some(objectSchema(Map("a" -> stringSchema())))),
        call("/v1/mixed", responseSchema = Some(objectSchema(Map("b" -> stringSchema()))))
      )
      entry should include(".output(z.union([")
    }

    it("excludes non-2xx bodies from .output") {
      val entry = endpoint(
        "GET",
        "/v1/things/{id}",
        call("/v1/things/{id}", responseSchema = Some(objectSchema(Map("ok" -> stringSchema())))),
        call("/v1/things/{id}", status = 404, responseSchema = Some(objectSchema(Map("error" -> stringSchema()))))
      )
      entry should include(""""ok": z.string()""")
      val outputPart = entry.substring(entry.indexOf(".output("))
      (outputPart should not).include(""""error"""")
    }
  }

  describe("declared errors (.errors)") {

    val problemSchema = objectSchema(Map("type" -> stringSchema(), "title" -> stringSchema()))

    it("emits nothing when only 2xx responses were captured") {
      val entry = endpoint("GET", "/v1/health", call("/v1/health"))
      (entry should not).include(".errors(")
    }

    it("declares one error per code extracted from the captured body (default field: type)") {
      val entry = endpoint(
        "POST",
        "/v1/things",
        call("/v1/things", method = "POST", status = 201),
        call(
          "/v1/things",
          method = "POST",
          status = 409,
          responseSchema = Some(problemSchema),
          responseBodyString = """{"type":"result:bid-too-low","status":409,"title":"Bid too low"}"""
        ),
        call(
          "/v1/things",
          method = "POST",
          status = 409,
          responseSchema = Some(problemSchema),
          responseBodyString = """{"type":"result:already-highest-bidder","status":409,"title":"Already highest"}"""
        )
      )
      entry should include(".errors({")
      entry should include("      'result:bid-too-low': {")
      entry should include("      'result:already-highest-bidder': {")
      entry should include("        status: 409")
      entry should include("        data: z.object(")
    }

    it("extracts the code from a configurable field") {
      val entry = generator.createContractForEndpoint(
        (
          (Some(Method("GET")), "/v1/things"),
          Seq(
            call("/v1/things"),
            call(
              "/v1/things",
              status = 404,
              responseSchema = Some(objectSchema(Map("code" -> stringSchema()))),
              responseBodyString = """{"code":"NOT_FOUND","message":"missing"}"""
            )
          )
        ),
        errorCodeField = "code"
      )
      entry should include("      'NOT_FOUND': {")
      entry should include("        status: 404")
    }

    it("skips error responses whose body carries no extractable code") {
      val entry = endpoint(
        "GET",
        "/v1/things",
        call("/v1/things"),
        call("/v1/things", status = 429)
      )
      (entry should not).include(".errors(")
    }

    it("uses the lowest status when the same code appears with several") {
      val entry = endpoint(
        "GET",
        "/v1/things",
        call("/v1/things"),
        call("/v1/things", status = 410, responseBodyString = """{"type":"gone-ish"}"""),
        call("/v1/things", status = 404, responseBodyString = """{"type":"gone-ish"}""")
      )
      entry should include("      'gone-ish': {")
      entry should include("        status: 404")
      (entry should not).include("status: 410")
    }

    it("omits data when no error body schema was captured") {
      val entry = endpoint(
        "GET",
        "/v1/things",
        call("/v1/things"),
        call("/v1/things", status = 404, responseBodyString = """{"type":"nope"}""")
      )
      entry should include("      'nope': {\n        status: 404\n      }")
    }
  }

  describe("literal discriminators in declared error data") {

    it("narrows the discriminator property to the declared code") {
      val problem = objectSchema(Map("type" -> stringSchema(), "title" -> stringSchema()))
      val entry   = endpoint(
        "POST",
        "/v1/things",
        call("/v1/things", method = "POST", status = 201),
        call(
          "/v1/things",
          method = "POST",
          status = 409,
          responseSchema = Some(problem),
          responseBodyString = """{"type":"result:bid-too-low","status":409}"""
        )
      )
      entry should include(""""type": z.enum(["result:bid-too-low"])""")
      (entry should not).include(""""type": z.string()""")
    }
  }

  describe("named schema hoisting (buildSchemaRefs)") {

    def dtoSchema(marker: String): BaklavaSchemaSerializable =
      objectSchema(Map(marker -> stringSchema())).copy(className = "AuctionDto")

    it("hoists an object schema that occurs more than once, named from its className") {
      val dto  = dtoSchema("a")
      val refs = generator.buildSchemaRefs(
        Seq(
          ((Some(Method("GET")), "/a"), Seq(call("/a", responseSchema = Some(dto)))),
          ((Some(Method("GET")), "/b"), Seq(call("/b", responseSchema = Some(dto))))
        ),
        "type"
      )
      refs.get(dto) shouldBe Some("auctionDtoSchema")
    }

    it("hoists single-occurrence named schemas too (every case class gets a schema + type)") {
      val dto  = dtoSchema("a")
      val refs = generator.buildSchemaRefs(
        Seq(((Some(Method("GET")), "/a"), Seq(call("/a", responseSchema = Some(dto))))),
        "type"
      )
      refs.get(dto) shouldBe Some("auctionDtoSchema")
    }

    it("suffixes colliding names deterministically") {
      val dto1 = dtoSchema("a")
      val dto2 = dtoSchema("b")
      val refs = generator.buildSchemaRefs(
        Seq(
          ((Some(Method("GET")), "/a"), Seq(call("/a", responseSchema = Some(dto1)), call("/a", responseSchema = Some(dto1)))),
          ((Some(Method("GET")), "/b"), Seq(call("/b", responseSchema = Some(dto2)), call("/b", responseSchema = Some(dto2))))
        ),
        "type"
      )
      refs.values.toSet should have size 2
      refs.values.count(_ == "auctionDtoSchema") shouldBe 1
      refs.values.filterNot(_ == "auctionDtoSchema").head should startWith("auctionDtoSchema")
    }

    it("hoists ONE shared base for error data declared under several codes") {
      val problem               = objectSchema(Map("type" -> stringSchema(), "title" -> stringSchema())).copy(className = "Error")
      def errCall(code: String) =
        call(
          "/a",
          method = "POST",
          status = 409,
          responseSchema = Some(problem),
          responseBodyString = s"""{"type":"$code","title":"t"}"""
        )
      val refs = generator.buildSchemaRefs(
        Seq(
          (
            (Some(Method("POST")), "/a"),
            Seq(call("/a", method = "POST", status = 201), errCall("result:bid-too-low"), errCall("result:auction-not-open"))
          )
        ),
        "type"
      )
      refs.keySet shouldBe Set(problem)
      refs(problem) shouldBe "errorSchema"
    }

    it("narrows a hoisted error base at the use site via .extend") {
      val problem  = objectSchema(Map("type" -> stringSchema(), "title" -> stringSchema())).copy(className = "Error")
      val refs     = Map(problem -> "errorSchema")
      val renderer = new TsZodRenderer(TsZodDialect.orpc, refs.get)
      val entry    = generator.declaredErrors(
        Seq(
          call("/a", method = "POST", status = 201),
          call(
            "/a",
            method = "POST",
            status = 409,
            responseSchema = Some(problem),
            responseBodyString = """{"type":"result:bid-too-low","title":"t"}"""
          )
        ),
        errorCodeField = "type",
        renderer = renderer,
        refs = refs
      )
      entry.get should include("""data: errorSchema.extend({type: z.enum(["result:bid-too-low"])})""")
      (entry.get should not).include("z.object(")
    }

    it("skips schemas with generic classNames") {
      val obj  = objectSchema(Map("a" -> stringSchema()))
      val refs = generator.buildSchemaRefs(
        Seq(
          ((Some(Method("GET")), "/a"), Seq(call("/a", responseSchema = Some(obj)))),
          ((Some(Method("GET")), "/b"), Seq(call("/b", responseSchema = Some(obj))))
        ),
        "type"
      )
      refs shouldBe empty
    }
  }

  describe("schema type exports") {

    it("pairs every hoisted schema with an inferred type export") {
      val dto     = objectSchema(Map("a" -> stringSchema())).copy(className = "AuctionDto")
      val content = TsSchemaRefs.schemasFileContent(Map(dto -> "auctionDtoSchema"), zodRenderer.zodDefinition)
      content should include("export type AuctionDto = z.infer<typeof auctionDtoSchema>;")
    }

    it("suffixes type names that would shadow TS globals") {
      val err     = objectSchema(Map("type" -> stringSchema())).copy(className = "Error")
      val content = TsSchemaRefs.schemasFileContent(Map(err -> "errorSchema"), zodRenderer.zodDefinition)
      content should include("export type ErrorType = z.infer<typeof errorSchema>;")
    }
  }

  describe("route metadata") {

    it("emits sorted distinct tags and the operationId") {
      val entry = endpoint(
        "GET",
        "/v1/things",
        call("/v1/things", tags = Seq("Things", "Admin"), operationId = Some("listThings"))
      )
      entry should include("      operationId: 'listThings'")
      entry should include("      tags: ['Admin', 'Things']")
    }

    it("omits tags and operationId when absent") {
      val entry = endpoint("GET", "/v1/things", call("/v1/things"))
      (entry should not).include("tags:")
      (entry should not).include("operationId:")
    }
  }

  describe("segment naming") {

    it("path parameters read as by<Param>, matching tsfetch's function names") {
      TsNaming.segmentKey("{auctionId}") shouldBe "byAuctionId"
      TsNaming.segmentKey(":userId") shouldBe "byUserId"
      TsNaming.segmentKey("{after-id}") shouldBe "byAfterId"
    }

    it("camelizes static segments on non-alphanumeric boundaries") {
      TsNaming.segmentKey("feature-flags") shouldBe "featureFlags"
      TsNaming.segmentKey("file.txt") shouldBe "fileTxt"
      TsNaming.segmentKey("auctions") shouldBe "auctions"
    }
  }

  describe("buildRouterTree() / modulesOf()") {

    def ep(method: String, path: String): ((Option[Method], String), Seq[BaklavaSerializableCall]) =
      ((Some(Method(method)), path), Seq(call(path, method = method)))

    it("nests endpoints by path segment with by<Param> keys") {
      val tree = TsPathRouter.buildRouterTree(
        Seq(ep("GET", "/v1/auctions"), ep("GET", "/v1/auctions/{auctionId}"), ep("POST", "/v1/auctions/{auctionId}/bids"))
      )
      val auctions = tree.children("v1").node.children("auctions").node
      auctions.procedures.keySet shouldBe Set("get")
      val byAuctionId = auctions.children("byAuctionId").node
      byAuctionId.procedures.keySet shouldBe Set("get")
      byAuctionId.children("bids").node.procedures.keySet shouldBe Set("post")
    }

    it("hash-suffixes a segment key when two distinct raw segments collapse to it") {
      val tree = TsPathRouter.buildRouterTree(Seq(ep("GET", "/users/by-id"), ep("GET", "/users/{id}")))
      val keys = tree.children("users").node.children.keySet
      keys should have size 2
      keys should contain("byId")
      keys.filterNot(_ == "byId").head should startWith("byId")
    }

    it("treats a version prefix as organizational: one module file per area below it") {
      val modules = TsPathRouter.modulesOf(TsPathRouter.buildRouterTree(Seq(ep("GET", "/v1/auctions"), ep("GET", "/v1/feature-flags"))))
      modules.map(m => (m.constName, m.fileSegments, m.mountPath)) shouldBe Seq(
        ("v1Auctions", List("v1", "auctions"), List("v1", "auctions")),
        ("v1FeatureFlags", List("v1", "featureFlags"), List("v1", "featureFlags"))
      )
    }

    it("gives a single-resource area one flat module file (only param children)") {
      val modules = TsPathRouter.modulesOf(TsPathRouter.buildRouterTree(Seq(ep("GET", "/health"), ep("GET", "/users/{userId}"))))
      modules.map(m => (m.constName, m.fileSegments, m.mountPath)) shouldBe Seq(
        ("health", List("health"), List("health")),
        ("users", List("users"), List("users"))
      )
    }

    it("explodes a non-version namespace (>=2 named sub-resources) into a folder, like admin") {
      val modules = TsPathRouter.modulesOf(
        TsPathRouter.buildRouterTree(
          Seq(ep("GET", "/admin/config"), ep("GET", "/admin/loggers/{name}"), ep("POST", "/admin/loggers/{name}"))
        )
      )
      modules.map(m => (m.constName, m.fileSegments, m.mountPath)) shouldBe Seq(
        ("adminConfig", List("admin", "config"), List("admin", "config")),
        ("adminLoggers", List("admin", "loggers"), List("admin", "loggers"))
      )
    }

    it("keeps a single named sub-resource flat (not a namespace)") {
      val modules = TsPathRouter.modulesOf(TsPathRouter.buildRouterTree(Seq(ep("POST", "/auth/login"))))
      modules.map(m => (m.constName, m.fileSegments)) shouldBe Seq(("auth", List("auth")))
    }
  }

  describe("zod()") {

    it("renders uuid, date-time and enum formats") {
      zodRenderer.zod(uuidSchema(required = true)) shouldBe "z.uuid()"
      zodRenderer.zod(stringSchema(format = Some("date-time"))) shouldBe "z.iso.datetime({ offset: true })"
      zodRenderer.zod(stringSchema(enumValues = Some(Set("b", "a")))) shouldBe """z.enum(["a","b"])"""
    }

    it("sorts object properties for deterministic output") {
      val a = zodRenderer.zod(objectSchema(Map("b" -> stringSchema(), "a" -> stringSchema())))
      val b = zodRenderer.zod(objectSchema(Map("a" -> stringSchema(), "b" -> stringSchema())))
      a shouldBe b
    }
  }

  private def endpoint(method: String, path: String, calls: BaklavaSerializableCall*): String =
    generator.createContractForEndpoint(((Some(Method(method)), path), calls.toSeq))

  private def call(
      path: String,
      method: String = "GET",
      status: Int = 200,
      pathParams: Seq[(String, BaklavaSchemaSerializable)] = Nil,
      queryParams: Seq[(String, BaklavaSchemaSerializable)] = Nil,
      bodySchema: Option[BaklavaSchemaSerializable] = None,
      responseSchema: Option[BaklavaSchemaSerializable] = None,
      responseBodyString: String = "",
      multipartParts: Option[Seq[BaklavaMultipartPartSerializable]] = None,
      tags: Seq[String] = Nil,
      operationId: Option[String] = None
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
        operationId = operationId,
        operationTags = tags,
        securitySchemes = Nil,
        bodySchema = bodySchema,
        bodyString = "",
        headersSeq = Nil,
        pathParametersSeq = pathParams.map { case (n, s) => BaklavaPathParamSerializable(n, None, s) },
        queryParametersSeq = queryParams.map { case (n, s) => BaklavaQueryParamSerializable(n, None, s) },
        responseDescription = None,
        responseHeaders = Nil,
        multipartFormData = multipartParts
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(status),
        headers = Seq.empty,
        bodyString = responseBodyString,
        requestContentType = multipartParts.map(_ => "multipart/form-data; boundary=baklava-multipart-boundary"),
        responseContentType = None,
        bodySchema = responseSchema
      )
    )

  private def stringSchema(
      description: Option[String] = None,
      enumValues: Option[Set[String]] = None,
      format: Option[String] = None
  ): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "String",
      `type` = SchemaType.StringType,
      format = format,
      properties = Map.empty,
      items = None,
      `enum` = enumValues,
      required = true,
      additionalProperties = false,
      default = None,
      description = description
    )

  private def intSchema(required: Boolean): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "Int",
      `type` = SchemaType.IntegerType,
      format = None,
      properties = Map.empty,
      items = None,
      `enum` = None,
      required = required,
      additionalProperties = false,
      default = None,
      description = None
    )

  private def uuidSchema(required: Boolean): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "UUID",
      `type` = SchemaType.StringType,
      format = Some("uuid"),
      properties = Map.empty,
      items = None,
      `enum` = None,
      required = required,
      additionalProperties = false,
      default = None,
      description = None
    )

  private def objectSchema(
      properties: Map[String, BaklavaSchemaSerializable],
      additionalPropertiesValueSchema: Option[BaklavaSchemaSerializable] = None
  ): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "Object",
      `type` = SchemaType.ObjectType,
      format = None,
      properties = properties,
      items = None,
      `enum` = None,
      required = true,
      additionalProperties = additionalPropertiesValueSchema.isDefined,
      default = None,
      description = None,
      additionalPropertiesSchema = additionalPropertiesValueSchema
    )
}
