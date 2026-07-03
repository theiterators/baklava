package pl.iterators.baklava.orpc

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

class BaklavaDslFormatterOrpcSpec extends AnyFunSpec with Matchers {

  private val generator = new BaklavaDslFormatterOrpc

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

    it("picks the lowest captured 2xx as successStatus") {
      val entry = endpoint(
        "POST",
        "/v1/things",
        call("/v1/things", method = "POST", status = 201),
        call("/v1/things", method = "POST", status = 200)
      )
      entry should include("      successStatus: 200")
    }
  }

  describe("createContractForEndpoint(): input groups") {

    it("emits params for path parameters") {
      val entry = endpoint(
        "GET",
        "/v1/auctions/{auctionId}",
        call("/v1/auctions/{auctionId}", pathParams = Seq("auctionId" -> uuidSchema(required = true)))
      )
      entry should include("      params: z.object({auctionId: z.string().uuid()})")
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
      entry should include(""""after-id": z.string().uuid().nullish()""")
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
      entry should include("      body: z.object({file: z.instanceof(File)})")
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

  describe("createErrorsForEndpoint()") {

    it("returns None when only 2xx responses were captured") {
      generator.createErrorsForEndpoint(
        ((Some(Method("GET")), "/v1/health"), Seq(call("/v1/health")))
      ) shouldBe None
    }

    it("maps non-2xx statuses to their captured schemas") {
      val entry = generator
        .createErrorsForEndpoint(
          (
            (Some(Method("POST")), "/v1/things"),
            Seq(
              call("/v1/things", method = "POST", status = 201),
              call("/v1/things", method = "POST", status = 404, responseSchema = Some(objectSchema(Map("title" -> stringSchema())))),
              call("/v1/things", method = "POST", status = 409, responseSchema = Some(objectSchema(Map("title" -> stringSchema()))))
            )
          )
        )
        .getOrElse(fail("expected errors entry"))
      entry should include("  post: {")
      entry should include("    404: z.object(")
      entry should include("    409: z.object(")
      (entry should not).include("201")
    }

    it("renders a bodyless error status as z.void()") {
      val entry = generator
        .createErrorsForEndpoint(
          ((Some(Method("GET")), "/v1/things"), Seq(call("/v1/things", status = 429)))
        )
        .getOrElse(fail("expected errors entry"))
      entry should include("    429: z.void()")
    }
  }

  describe("contractNameFromSymbolicPath()") {

    it("derives names identically to the ts-rest formatter") {
      generator.contractNameFromSymbolicPath("/v1/auctions/{auctionId}/bids") shouldBe "v1-auctions---auctionId-bids"
      generator.contractNameFromSymbolicPath("/") shouldBe "root"
      generator.contractNameFromSymbolicPath("/v1/file.txt") shouldBe "v1-file---txt"
    }
  }

  describe("zod()") {

    it("renders uuid, date-time and enum formats") {
      generator.zod(uuidSchema(required = true)) shouldBe "z.string().uuid()"
      generator.zod(stringSchema(format = Some("date-time"))) shouldBe "z.coerce.date()"
      generator.zod(stringSchema(enumValues = Some(Set("b", "a")))) shouldBe """z.enum(["a","b"])"""
    }

    it("sorts object properties for deterministic output") {
      val a = generator.zod(objectSchema(Map("b" -> stringSchema(), "a" -> stringSchema())))
      val b = generator.zod(objectSchema(Map("a" -> stringSchema(), "b" -> stringSchema())))
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
      multipartParts: Option[Seq[BaklavaMultipartPartSerializable]] = None
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
        operationTags = Nil,
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
        bodyString = "",
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
