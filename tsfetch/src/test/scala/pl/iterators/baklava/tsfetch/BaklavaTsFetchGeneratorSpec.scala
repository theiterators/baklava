package pl.iterators.baklava.tsfetch

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import java.io.File
import java.nio.file.Files

class BaklavaTsFetchGeneratorSpec extends AnyFunSpec with Matchers {

  describe("BaklavaDslFormatterTsFetch") {

    it("creates the directory structure with a folder per tag") {
      val call = getCall("/users", tag = Some("Users"))
      new BaklavaDslFormatterTsFetch().create(Map.empty, Seq(call))
      new File("target/baklava/tsfetch/tsconfig.json").exists() shouldBe true
      new File("target/baklava/tsfetch/package.json").exists() shouldBe true
      new File("target/baklava/tsfetch/src/client.ts").exists() shouldBe true
      new File("target/baklava/tsfetch/src/index.ts").exists() shouldBe true
      new File("target/baklava/tsfetch/src/users/endpoints.ts").exists() shouldBe true
    }

    it("puts untagged operations into default/endpoints.ts") {
      new BaklavaDslFormatterTsFetch().create(Map.empty, Seq(getCall("/health", tag = None)))
      new File("target/baklava/tsfetch/src/default/endpoints.ts").exists() shouldBe true
    }

    it("names endpoint functions from operationId when present, else method+pascalPath") {
      val usersTs = generateAndRead(
        "src/users/endpoints.ts",
        Seq(
          getCall("/users", tag = Some("Users"), operationId = Some("listUsers")),
          getCall("/users/{userId}", tag = Some("Users"), pathParams = Seq("userId"))
        )
      )
      usersTs should include("export async function listUsers(")
      usersTs should include("export async function getUsersByUserId(")
    }

    it("rewrites {name} path segments to encodeURIComponent substitution in the URL construction") {
      val ts = generateAndRead(
        "src/users/endpoints.ts",
        Seq(getCall("/users/{userId}", tag = Some("Users"), pathParams = Seq("userId")))
      )
      ts should include("encodeURIComponent(String(params.userId))")
    }

    it("emits the 2xx response schema as the declared return type") {
      val schema = BaklavaSchemaSerializable(Schema.stringSchema)
      val base   = getCall("/hello", tag = Some("Users"))
      val call   = base.copy(response = base.response.copy(bodySchema = Some(schema)))
      val ts     = generateAndRead("src/users/endpoints.ts", Seq(call))
      ts should include("Promise<string>")
    }

    it("returns Promise<void> when there's no 2xx body schema") {
      val ts = generateAndRead("src/users/endpoints.ts", Seq(getCall("/noop", tag = Some("Users"))))
      ts should include("Promise<void>")
    }

    it("puts a type used by a single tag in that tag's types.ts") {
      cleanSrc()
      val userSchema = namedObject("User", Map("id" -> Schema.intSchema, "name" -> Schema.stringSchema))
      val base       = getCall("/users", tag = Some("Users"))
      val call       = base.copy(response = base.response.copy(bodySchema = Some(userSchema)))

      new BaklavaDslFormatterTsFetch().create(Map.empty, Seq(call))
      val typesTs = new String(Files.readAllBytes(new File("target/baklava/tsfetch/src/users/types.ts").toPath))
      typesTs should include("export interface User {")
      typesTs should include("id: number;")
      typesTs should include("name: string;")
      new File("target/baklava/tsfetch/src/common/types.ts").exists() shouldBe false
    }

    it("puts a type used by two or more tags in common/types.ts and imports it from there") {
      cleanSrc()
      val errSchema = namedObject("ErrorResponse", Map("message" -> Schema.stringSchema))

      val callUsers = getCall("/users", tag = Some("Users"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))
      val callProjects = getCall("/projects", tag = Some("Projects"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))

      new BaklavaDslFormatterTsFetch().create(Map.empty, Seq(callUsers, callProjects))

      val commonTs = new String(Files.readAllBytes(new File("target/baklava/tsfetch/src/common/types.ts").toPath))
      commonTs should include("export interface ErrorResponse {")

      val usersEndpoints = new String(Files.readAllBytes(new File("target/baklava/tsfetch/src/users/endpoints.ts").toPath))
      usersEndpoints should include("""import type { ErrorResponse } from "../common/types";""")

      val projectsEndpoints = new String(Files.readAllBytes(new File("target/baklava/tsfetch/src/projects/endpoints.ts").toPath))
      projectsEndpoints should include("""import type { ErrorResponse } from "../common/types";""")

      new File("target/baklava/tsfetch/src/users/types.ts").exists() shouldBe false
      new File("target/baklava/tsfetch/src/projects/types.ts").exists() shouldBe false
    }

    it("includes bearer authorization in client.ts via authHeaders()") {
      val clientTs = generateAndRead("src/client.ts", Seq(getCall("/x", tag = Some("X"))))
      clientTs should include("Authorization")
      clientTs should include("Bearer")
      clientTs should include("this.bearerToken")
    }

    it("index.ts re-exports client, each tag's endpoints, and common/local type namespaces") {
      val errSchema  = namedObject("ErrorResponse", Map("message" -> Schema.stringSchema))
      val userSchema = namedObject("User", Map("id" -> Schema.intSchema))

      val usersCall = getCall("/users", tag = Some("Users"), operationId = Some("listUsers"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(userSchema))))
      val projectsCall = getCall("/projects", tag = Some("Projects"), operationId = Some("listProjects"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))
      val extraShared = getCall("/errors", tag = Some("Users"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))

      val indexTs = generateAndRead("src/index.ts", Seq(usersCall, projectsCall, extraShared))
      indexTs should include("""export * from "./client";""")
      indexTs should include("""export * as Common from "./common/types";""")
      indexTs should include("""export * from "./users/endpoints";""")
      indexTs should include("""export * from "./projects/endpoints";""")
      indexTs should include("""export * as Users from "./users/types";""")
    }
  }

  private def namedObject(name: String, props: Map[String, PrimitiveSchema[?]]): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = name,
      `type` = SchemaType.ObjectType,
      format = None,
      properties = props.map { case (k, s) => k -> BaklavaSchemaSerializable(s) },
      items = None,
      `enum` = None,
      required = true,
      additionalProperties = false,
      default = None,
      description = None
    )

  implicit private class Letable[A](val a: A) {
    def let(f: A => A): A = f(a)
  }

  private def getCall(
      path: String,
      tag: Option[String],
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

  private def cleanSrc(): Unit = {
    val dir = new File("target/baklava/tsfetch/src")
    if (dir.exists()) deleteRecursively(dir)
  }

  private def deleteRecursively(f: File): Unit = {
    if (f.isDirectory) f.listFiles().foreach(deleteRecursively)
    f.delete()
    ()
  }

  private def generateAndRead(relPath: String, calls: Seq[BaklavaSerializableCall], config: Map[String, String] = Map.empty): String = {
    new BaklavaDslFormatterTsFetch().create(config, calls)
    val f = new File(s"target/baklava/tsfetch/$relPath")
    new String(Files.readAllBytes(f.toPath))
  }
}
