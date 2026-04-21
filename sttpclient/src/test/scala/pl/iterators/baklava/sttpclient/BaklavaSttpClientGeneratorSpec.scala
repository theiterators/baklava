package pl.iterators.baklava.sttpclient

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import java.io.File
import java.nio.file.Files

class BaklavaSttpClientGeneratorSpec extends AnyFunSpec with Matchers {

  describe("BaklavaDslFormatterSttpClient") {

    it("puts the endpoint object under a sub-package named after its tag") {
      cleanSrc()
      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(getCall("/users", tag = Some("Users"))))
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/UsersEndpoints.scala").exists() shouldBe true
      new File("target/baklava/sttpclient/README.md").exists() shouldBe true
    }

    it("puts untagged operations into default/DefaultEndpoints.scala") {
      cleanSrc()
      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(getCall("/health", tag = None)))
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/default/DefaultEndpoints.scala").exists() shouldBe true
    }

    it("emits one `def` per endpoint, named from operationId when present") {
      cleanSrc()
      val content = generateAndRead(
        "src/main/scala/baklavaclient/users/UsersEndpoints.scala",
        Seq(getCall("/users", tag = Some("Users"), operationId = Some("listUsers")))
      )
      content should include("def listUsers(")
      content should include("object UsersEndpoints {")
      content should include("package baklavaclient.users")
    }

    it("sanitizes fallback def names derived from paths when operationId is missing") {
      cleanSrc()
      val hyphenatedContent = generateAndRead(
        "src/main/scala/baklavaclient/users/UsersEndpoints.scala",
        Seq(getCall("/user-profile", tag = Some("Users"), operationId = None))
      )
      hyphenatedContent should include("def getUserProfile(")
      hyphenatedContent should not include "def getUser-profile("

      cleanSrc()
      val versionedContent = generateAndRead(
        "src/main/scala/baklavaclient/users/UsersEndpoints.scala",
        Seq(getCall("/v1/users", tag = Some("Users"), operationId = None))
      )
      versionedContent should include("def getV1Users(")
      versionedContent should not include "def getV1/users("

      cleanSrc()
      val dottedContent = generateAndRead(
        "src/main/scala/baklavaclient/users/UsersEndpoints.scala",
        Seq(getCall("/api/users.json", tag = Some("Users"), operationId = None))
      )
      dottedContent should include("def getApiUsersJson(")
      dottedContent should not include "def getApiUsers.json("
    }

    it("rewrites {name} path segments as Scala string interpolation against sttp `addPath`") {
      cleanSrc()
      val content = generateAndRead(
        "src/main/scala/baklavaclient/users/UsersEndpoints.scala",
        Seq(getCall("/users/{userId}", tag = Some("Users"), pathParams = Seq("userId")))
      )
      content should include("""baseUri.addPath("users", s"$userId")""")
      content should include("userId: String")
    }

    it("puts a type used by a single tag in that tag's Types.scala") {
      cleanSrc()
      val userSchema = namedObject("User", Map("id" -> Schema.longSchema, "name" -> Schema.stringSchema))
      val base       = getCall("/users", tag = Some("Users"))
      val call       = base.copy(response = base.response.copy(bodySchema = Some(userSchema)))

      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(call))
      val types =
        new String(Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/dtos.scala").toPath))
      types should include("package baklavaclient.users")
      types should include("final case class User(")
      types should include("id: Long")
      types should include("name: String")
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/common/dtos.scala").exists() shouldBe false
    }

    it("puts a type used by two or more tags in common/dtos.scala and imports it from each tag's Endpoints.scala") {
      cleanSrc()
      val errSchema = namedObject("ErrorResponse", Map("message" -> Schema.stringSchema))

      val callUsers = getCall("/users", tag = Some("Users"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))
      val callProjects = getCall("/projects", tag = Some("Projects"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))

      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(callUsers, callProjects))

      val common =
        new String(Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/common/dtos.scala").toPath))
      common should include("package baklavaclient.common")
      common should include("final case class ErrorResponse(")

      val users =
        new String(
          Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/UsersEndpoints.scala").toPath)
        )
      users should include("import baklavaclient.common.ErrorResponse")

      val projects =
        new String(
          Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/projects/ProjectsEndpoints.scala").toPath)
        )
      projects should include("import baklavaclient.common.ErrorResponse")

      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/dtos.scala").exists() shouldBe false
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/projects/dtos.scala").exists() shouldBe false
    }

    it("honors the `sttp-client-package` config key as the emitted package") {
      cleanSrc()
      new BaklavaDslFormatterSttpClient().create(
        Map("sttp-client-package" -> "com.example.api"),
        Seq(getCall("/users", tag = Some("Users")))
      )
      val content = new String(
        Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/com/example/api/users/UsersEndpoints.scala").toPath)
      )
      content should include("package com.example.api.users")
    }

    it("adds a bearer token credential parameter and Authorization header for Bearer-secured endpoints") {
      cleanSrc()
      val scheme  = BaklavaSecuritySchemaSerializable("bearerAuth", BaklavaSecuritySerializable(httpBearer = Some(HttpBearer())))
      val base    = getCall("/me", tag = Some("Users"))
      val call    = base.copy(request = base.request.copy(securitySchemes = Seq(scheme)))
      val content = generateAndRead("src/main/scala/baklavaclient/users/UsersEndpoints.scala", Seq(call))
      content should include("bearerAuthToken: String")
      content should include("""      .header("Authorization", s"Bearer ${bearerAuthToken}")""")
    }

    it("uses valid identifiers for security credentials when the scheme name is a Scala reserved word") {
      cleanSrc()
      val scheme  = BaklavaSecuritySchemaSerializable("type", BaklavaSecuritySerializable(httpBearer = Some(HttpBearer())))
      val base    = getCall("/me", tag = Some("Users"))
      val call    = base.copy(request = base.request.copy(securitySchemes = Seq(scheme)))
      val content = generateAndRead("src/main/scala/baklavaclient/users/UsersEndpoints.scala", Seq(call))
      content should include("typeToken: String")
      content should not include "`type`Token"
    }

    it("honors the captured request content-type on the body .contentType call instead of hard-coding JSON") {
      cleanSrc()
      val body = namedObject("Upload", Map("name" -> Schema.stringSchema))
      val base = getCall("/upload", tag = Some("Users"))
      val call = base.copy(
        request = base.request.copy(bodySchema = Some(body)),
        response = base.response.copy(requestContentType = Some("multipart/form-data"))
      )
      val content = generateAndRead("src/main/scala/baklavaclient/users/UsersEndpoints.scala", Seq(call))
      content should include(""".contentType("multipart/form-data")""")
      content should not include """.contentType("application/json")"""
    }

    it("wires `*InCookie` OAuth/OIDC schemes via .cookie(cookieName, token) with two credential parameters") {
      cleanSrc()
      val flows  = OAuthFlows()
      val scheme = BaklavaSecuritySchemaSerializable(
        "sessionOAuth",
        BaklavaSecuritySerializable(oAuth2InCookie = Some(OAuth2InCookie(flows)))
      )
      val base    = getCall("/me", tag = Some("Users"))
      val call    = base.copy(request = base.request.copy(securitySchemes = Seq(scheme)))
      val content =
        generateAndRead("src/main/scala/baklavaclient/users/UsersEndpoints.scala", Seq(call))
      content should include("sessionOAuthCookieName: String")
      content should include("sessionOAuthToken: String")
      content should include(".cookie(sessionOAuthCookieName, sessionOAuthToken)")
    }

    it("emits a typed `body: T` parameter + circe `asJson[T]` response when request/response schemas are named case classes") {
      cleanSrc()
      val userReq  = namedObject("CreateUserRequest", Map("name" -> Schema.stringSchema))
      val userResp = namedObject("User", Map("id" -> Schema.longSchema, "name" -> Schema.stringSchema))
      val base     = getCall("/users", tag = Some("Users")).let(c => c.copy(request = c.request.copy(method = Some(Method("POST")))))
      val call     = base.copy(
        request = base.request.copy(bodySchema = Some(userReq)),
        response = base.response.copy(
          status = StatusCode(201),
          bodySchema = Some(userResp),
          requestContentType = Some("application/json"),
          responseContentType = Some("application/json")
        )
      )

      val content = generateAndRead("src/main/scala/baklavaclient/users/UsersEndpoints.scala", Seq(call))
      content should include("import sttp.client4.circe._")
      content should include("import io.circe.syntax._")
      content should include("body: CreateUserRequest")
      content should include(".body(body.asJson.noSpaces)")
      content should not include "bodyJson"
      content should include("Request[Either[ResponseException[String], User]]")
      content should include(".response(asJson[User])")
    }

    it("keeps the raw `bodyJson: String` path for endpoints whose schema isn't a named case class") {
      cleanSrc()
      val content = generateAndRead(
        "src/main/scala/baklavaclient/users/UsersEndpoints.scala",
        Seq(getCall("/users", tag = Some("Users")))
      )
      content should not include "import sttp.client4.circe._"
      content should include("Request[Either[String, String]]")
    }

    it("falls back to .method(Method(...), uri) for HTTP verbs outside the well-known set") {
      cleanSrc()
      val base    = getCall("/resource", tag = Some("Users")).let(c => c.copy(request = c.request.copy(method = Some(Method("PROPFIND")))))
      val content = generateAndRead("src/main/scala/baklavaclient/users/UsersEndpoints.scala", Seq(base))
      content should include("""sttp.model.Method("PROPFIND")""")
      content should not include ".propfind("
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

  private def cleanSrc(): Unit = {
    val dir = new File("target/baklava/sttpclient")
    if (dir.exists()) deleteRecursively(dir)
  }

  private def deleteRecursively(f: File): Unit = {
    if (f.isDirectory) f.listFiles().foreach(deleteRecursively)
    f.delete()
    ()
  }

  private def getCall(
      path: String,
      tag: Option[String],
      operationId: Option[String] = None,
      pathParams: Seq[String] = Nil
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

  private def generateAndRead(relPath: String, calls: Seq[BaklavaSerializableCall], config: Map[String, String] = Map.empty): String = {
    new BaklavaDslFormatterSttpClient().create(config, calls)
    val f = new File(s"target/baklava/sttpclient/$relPath")
    new String(Files.readAllBytes(f.toPath))
  }
}
