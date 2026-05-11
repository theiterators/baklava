package pl.iterators.baklava.tsrest

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import java.io.File
import java.nio.file.Files

class BaklavaDslFormatterTsRestSpec extends AnyFunSpec with Matchers {

  private val generator = new BaklavaDslFormatterTsRest

  describe("zod(): schema.description rendering") {

    it("escapes backslashes inside .describe(\"...\") so they survive TypeScript string interpretation") {
      val schema = stringSchema(description = Some("""C:\Users\test""" + "\n" + "next line"))
      val out    = generator.zod(schema)
      // Input backslashes become `\\` (pair). Input newline becomes `\n` (one backslash + n).
      // TypeScript parses "C:\\Users\\test\nnext line" back to the original string.
      out should include(""".describe("C:\\Users\\test\nnext line")""")
      // Sanity: the emitted TS source should not contain a real newline inside the string literal.
      val emittedString = out.substring(out.indexOf(".describe("))
      emittedString should not contain '\n'
    }

    it("escapes double quotes inside .describe(\"...\")") {
      val schema = stringSchema(description = Some("""He said "hi""""))
      val out    = generator.zod(schema)
      out should include(""".describe("He said \"hi\"")""")
    }
  }

  describe("zod(): z.enum values") {

    it("escapes embedded double quotes in enum literals") {
      val schema = stringSchema(enumValues = Some(Set("foo", "weird\"quote")))
      val out    = generator.zod(schema)
      // Both values quoted; the " inside is escaped as \"
      out should startWith("z.enum([")
      out should include(""""weird\"quote"""")
      out should include(""""foo"""")
    }

    it("escapes backslashes in enum literals") {
      val schema = stringSchema(enumValues = Some(Set("""C:\tmp""")))
      val out    = generator.zod(schema)
      out should include(""""C:\\tmp"""")
    }

    it("produces deterministic (sorted) enum order") {
      val a = generator.zod(stringSchema(enumValues = Some(Set("c", "a", "b"))))
      val b = generator.zod(stringSchema(enumValues = Some(Set("b", "c", "a"))))
      a shouldBe b
      a should include("""z.enum(["a","b","c"])""")
    }
  }

  describe("zod(): object property keys") {

    it("quotes object property keys so hyphens/digits/reserved words are valid TypeScript") {
      val schema = objectSchema(
        Map(
          "content-type" -> stringSchema(),
          "2fa"          -> stringSchema(),
          "okName"       -> stringSchema()
        )
      )
      val out = generator.zod(schema)
      out should include(""""content-type": z.string()""")
      out should include(""""2fa": z.string()""")
      out should include(""""okName": z.string()""")
    }

    it("sorts object properties alphabetically for deterministic output") {
      val schemaA = objectSchema(Map("b" -> stringSchema(), "a" -> stringSchema(), "c" -> stringSchema()))
      val schemaB = objectSchema(Map("c" -> stringSchema(), "a" -> stringSchema(), "b" -> stringSchema()))
      generator.zod(schemaA) shouldBe generator.zod(schemaB)

      val out         = generator.zod(schemaA)
      val positionOfA = out.indexOf("\"a\"")
      val positionOfB = out.indexOf("\"b\"")
      val positionOfC = out.indexOf("\"c\"")
      positionOfA should (be < positionOfB and be < positionOfC)
      positionOfB should be < positionOfC
    }
  }

  describe("collapseZodUnion") {

    it("preserves non-object variants alongside object variants (regression)") {
      val out = generator.collapseZodUnion(Seq("z.string()", "z.object({foo: z.string()})"))
      out should startWith("z.union([")
      out should include("z.string()")
      out should include("z.object({foo: z.string()})")
    }

    it("emits a single entry without wrapping when only one distinct variant is present") {
      generator.collapseZodUnion(Seq("z.string()")) shouldBe "z.string()"
      // Duplicates collapse.
      generator.collapseZodUnion(Seq("z.string()", "z.string()")) shouldBe "z.string()"
    }

    it("emits z.undefined() on empty input") {
      generator.collapseZodUnion(Nil) shouldBe "z.undefined()"
    }
  }

  describe("contractNameFromSymbolicPath") {

    it("preserves existing non-collision behavior") {
      generator.contractNameFromSymbolicPath("/pets") shouldBe "pets"
      generator.contractNameFromSymbolicPath("/pets/{id}") shouldBe "pets---id"
      generator.contractNameFromSymbolicPath("/") shouldBe "root"
    }
  }

  describe("toTsRestPath") {

    it("converts simple {name} placeholders to :name") {
      generator.toTsRestPath("/users/{id}") shouldBe "/users/:id"
      generator.toTsRestPath("/a/{x}/b/{y}") shouldBe "/a/:x/b/:y"
    }

    it("converts placeholder names containing hyphens, dots, and underscores (regression)") {
      generator.toTsRestPath("/users/{user-id}") shouldBe "/users/:user-id"
      generator.toTsRestPath("/files/{file.name}") shouldBe "/files/:file.name"
      generator.toTsRestPath("/events/{event_id}") shouldBe "/events/:event_id"
    }

    it("leaves paths without placeholders untouched") {
      generator.toTsRestPath("/health") shouldBe "/health"
      generator.toTsRestPath("/") shouldBe "/"
    }

    it("does not rewrite a lone brace or a brace span that contains a slash") {
      generator.toTsRestPath("/foo{bar") shouldBe "/foo{bar"
      generator.toTsRestPath("/{a/b}") shouldBe "/{a/b}"
    }
  }

  describe("tsObjectKey") {

    it("leaves valid JS identifiers bare") {
      generator.tsObjectKey("status") shouldBe "status"
      generator.tsObjectKey("sellerId") shouldBe "sellerId"
      generator.tsObjectKey("_private") shouldBe "_private"
      generator.tsObjectKey("$ref") shouldBe "$ref"
      generator.tsObjectKey("page2") shouldBe "page2"
      // Reserved words are valid object keys (bare) at both runtime and type level.
      generator.tsObjectKey("class") shouldBe "class"
    }

    it("quotes keys with characters that aren't valid in a JS identifier") {
      generator.tsObjectKey("seller-id") shouldBe "\"seller-id\""
      generator.tsObjectKey("X-Forwarded-For") shouldBe "\"X-Forwarded-For\""
      generator.tsObjectKey("2fa") shouldBe "\"2fa\""
      generator.tsObjectKey("a.b") shouldBe "\"a.b\""
      generator.tsObjectKey("") shouldBe "\"\""
    }

    it("escapes embedded quotes/backslashes when it has to quote") {
      generator.tsObjectKey("""weird"key""") shouldBe """"weird\"key""""
      generator.tsObjectKey("""back\slash-x""") shouldBe """"back\\slash-x""""
    }
  }

  describe("buildParamsZod (query/header/path keys)") {

    it("quotes a kebab-case query-parameter key so the generated z.object is valid TypeScript (issue #105)") {
      val out = generator
        .buildParamsZod[BaklavaQueryParamSerializable](
          Seq(Seq(queryParam("seller-id", uuidSchema(required = false)), queryParam("status", stringSchema().copy(required = false)))),
          _.name,
          _.schema
        )
        .getOrElse(fail("expected a query schema"))
      out shouldBe """z.object({"seller-id": z.string().uuid().nullish(), status: z.string().nullish()})"""
    }

    it("returns None when no call carries any parameters") {
      generator.buildParamsZod[BaklavaQueryParamSerializable](Seq(Nil, Nil), _.name, _.schema) shouldBe None
    }
  }

  describe("renderMultipartBody") {

    it("emits z.instanceof(File) for file parts and z.string() for text parts, sorted by name") {
      generator.renderMultipartBody(
        Seq(
          BaklavaMultipartPartSerializable("photo", isFile = true),
          BaklavaMultipartPartSerializable("caption", isFile = false)
        )
      ) shouldBe "z.object({caption: z.string(), photo: z.instanceof(File)})"
    }

    it("quotes part names that aren't valid identifiers and de-duplicates by name") {
      generator.renderMultipartBody(
        Seq(
          BaklavaMultipartPartSerializable("file-1", isFile = true),
          BaklavaMultipartPartSerializable("file-1", isFile = true)
        )
      ) shouldBe """z.object({"file-1": z.instanceof(File)})"""
    }

    it("emits an empty object when the multipart body has no parts") {
      generator.renderMultipartBody(Nil) shouldBe "z.object({})"
    }
  }

  describe("create(): end-to-end contract emission") {

    it("quotes a kebab-case query key in the generated contract file (issue #105)") {
      val ts = generateAndRead(
        "v1-auctions.contract.ts",
        Seq(
          getCall(
            "/v1/auctions",
            queryParams = Seq("status" -> stringSchema().copy(required = false), "seller-id" -> uuidSchema(required = false))
          )
        )
      )
      ts should include("""query: z.object({status: z.string().nullish(), "seller-id": z.string().uuid().nullish()}),""")
    }

    it("emits contentType: 'multipart/form-data' and named part fields for a multipart body (issue #106)") {
      val ts = generateAndRead(
        "v1-auctions---auctionId-images.contract.ts",
        Seq(
          getCall(
            "/v1/auctions/{auctionId}/images",
            method = "POST",
            pathParams = Seq("auctionId" -> uuidSchema(required = true)),
            multipartParts = Some(
              Seq(
                BaklavaMultipartPartSerializable("file", isFile = true),
                BaklavaMultipartPartSerializable("caption", isFile = false)
              )
            )
          )
        )
      )
      // `contentType` is emitted immediately before `body`.
      ts should include("    contentType: 'multipart/form-data',\n    body: z.object({caption: z.string(), file: z.instanceof(File)}),\n")
    }

    it("still emits an empty object body (with the content type) for a multipart body with no parts") {
      val ts = generateAndRead(
        "v1-blank.contract.ts",
        Seq(getCall("/v1/blank", method = "POST", multipartParts = Some(Nil)))
      )
      ts should include("    contentType: 'multipart/form-data',\n    body: z.object({}),\n")
    }
  }

  private def queryParam(name: String, schema: BaklavaSchemaSerializable): BaklavaQueryParamSerializable =
    BaklavaQueryParamSerializable(name, None, schema)

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

  private def getCall(
      path: String,
      method: String = "GET",
      pathParams: Seq[(String, BaklavaSchemaSerializable)] = Nil,
      queryParams: Seq[(String, BaklavaSchemaSerializable)] = Nil,
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
        bodySchema = None,
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
        status = StatusCode(if (method == "POST") 204 else 200),
        headers = Seq.empty,
        bodyString = "",
        requestContentType = multipartParts.map(_ => "multipart/form-data; boundary=baklava-multipart-boundary"),
        responseContentType = None,
        bodySchema = None
      )
    )

  private def generateAndRead(relContractPath: String, calls: Seq[BaklavaSerializableCall]): String = {
    new BaklavaDslFormatterTsRest().create(Map.empty, calls)
    new String(Files.readAllBytes(new File(s"target/baklava/tsrest/src/$relContractPath").toPath))
  }

  private def stringSchema(description: Option[String] = None, enumValues: Option[Set[String]] = None): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "String",
      `type` = SchemaType.StringType,
      format = None,
      properties = Map.empty,
      items = None,
      `enum` = enumValues,
      required = true,
      additionalProperties = false,
      default = None,
      description = description
    )

  private def objectSchema(properties: Map[String, BaklavaSchemaSerializable]): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "Object",
      `type` = SchemaType.ObjectType,
      format = None,
      properties = properties,
      items = None,
      `enum` = None,
      required = true,
      additionalProperties = false,
      default = None,
      description = None
    )
}
