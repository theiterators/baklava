package pl.iterators.baklava.tsfetch

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import java.io.File
import java.nio.file.Files

class BaklavaTsFetchGeneratorSpec extends AnyFunSpec with Matchers {

  describe("BaklavaDslFormatterTsFetch") {

    it("creates the directory structure and canonical static files") {
      val call = getCall("/users", tag = Some("Users"))
      new BaklavaDslFormatterTsFetch().create(Map.empty, Seq(call))
      new File("target/baklava/tsfetch/tsconfig.json").exists() shouldBe true
      new File("target/baklava/tsfetch/package.json").exists() shouldBe true
      new File("target/baklava/tsfetch/src/client.ts").exists() shouldBe true
      new File("target/baklava/tsfetch/src/index.ts").exists() shouldBe true
      new File("target/baklava/tsfetch/src/users.ts").exists() shouldBe true
    }

    it("puts untagged operations into default.ts") {
      new BaklavaDslFormatterTsFetch().create(Map.empty, Seq(getCall("/health", tag = None)))
      new File("target/baklava/tsfetch/src/default.ts").exists() shouldBe true
    }

    it("names endpoint functions from operationId when present, else method+pascalPath") {
      val usersTs = generateAndRead(
        "src/users.ts",
        Seq(
          getCall("/users", tag = Some("Users"), operationId = Some("listUsers")),
          getCall("/users/{userId}", tag = Some("Users"), pathParams = Seq("userId"))
        )
      )
      usersTs should include("export async function listUsers(")
      usersTs should include("export async function getUsersByUserId(")
    }

    it("rewrites {name} path segments to encodeURIComponent substitution in the URL construction") {
      val ts = generateAndRead("src/users.ts", Seq(getCall("/users/{userId}", tag = Some("Users"), pathParams = Seq("userId"))))
      ts should include("encodeURIComponent(String(params.userId))")
    }

    it("emits the 2xx response schema as the declared return type") {
      val schema = BaklavaSchemaSerializable(Schema.stringSchema)
      val base   = getCall("/hello", tag = Some("Users"))
      val call   = base.copy(response = base.response.copy(bodySchema = Some(schema)))
      val ts     = generateAndRead("src/users.ts", Seq(call))
      ts should include("Promise<string>")
    }

    it("returns Promise<void> when there's no 2xx body schema") {
      val ts = generateAndRead("src/users.ts", Seq(getCall("/noop", tag = Some("Users"))))
      ts should include("Promise<void>")
    }

    it("declares a named interface in types.ts for each unique object schema used") {
      val userSchema = BaklavaSchemaSerializable(
        className = "User",
        `type` = SchemaType.ObjectType,
        format = None,
        properties = Map(
          "id"   -> BaklavaSchemaSerializable(Schema.intSchema),
          "name" -> BaklavaSchemaSerializable(Schema.stringSchema)
        ),
        items = None,
        `enum` = None,
        required = true,
        additionalProperties = false,
        default = None,
        description = None
      )
      val base    = getCall("/users", tag = Some("Users"))
      val call    = base.copy(response = base.response.copy(bodySchema = Some(userSchema)))
      val typesTs = generateAndRead("src/types.ts", Seq(call))
      typesTs should include("export interface User {")
      typesTs should include("id: number;")
      typesTs should include("name: string;")
    }

    it("includes bearer authorization in client.ts via authHeaders()") {
      val clientTs = generateAndRead("src/client.ts", Seq(getCall("/x", tag = Some("X"))))
      clientTs should include("Authorization")
      clientTs should include("Bearer")
      clientTs should include("this.bearerToken")
    }

    it("index.ts re-exports client and each tag module") {
      val indexTs = generateAndRead(
        "src/index.ts",
        Seq(
          getCall("/users", tag = Some("Users"), operationId = Some("listUsers")),
          getCall("/projects", tag = Some("Projects"), operationId = Some("listProjects"))
        )
      )
      indexTs should include("""export * from "./client";""")
      indexTs should include("""export * from "./users";""")
      indexTs should include("""export * from "./projects";""")
    }
  }

  private def getCall(
      path: String,
      tag: Option[String] = None,
      operationId: Option[String] = None,
      pathParams: Seq[String] = Nil,
      queryParams: Seq[String] = Nil
  ): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = path,
        path = path,
        pathDescription = None,
        pathSummary = None,
        method = Some(Method("GET")),
        operationDescription = None,
        operationSummary = None,
        operationId = operationId,
        operationTags = tag.toSeq,
        securitySchemes = Nil,
        bodySchema = None,
        bodyString = "",
        headersSeq = Nil,
        pathParametersSeq = pathParams.map(n => BaklavaPathParamSerializable(n, None, BaklavaSchemaSerializable(Schema.stringSchema))),
        queryParametersSeq = queryParams.map(n => BaklavaQueryParamSerializable(n, None, BaklavaSchemaSerializable(Schema.stringSchema))),
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

  private def generateAndRead(relPath: String, calls: Seq[BaklavaSerializableCall]): String = {
    new BaklavaDslFormatterTsFetch().create(Map.empty, calls)
    val f = new File(s"target/baklava/tsfetch/$relPath")
    new String(Files.readAllBytes(f.toPath))
  }
}
