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
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/Endpoints.scala").exists() shouldBe true
      new File("target/baklava/sttpclient/README.md").exists() shouldBe true
    }

    it("puts untagged operations into default/Endpoints.scala") {
      cleanSrc()
      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(getCall("/health", tag = None)))
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/default/Endpoints.scala").exists() shouldBe true
    }

    it("emits one `def` per endpoint, named from operationId when present") {
      cleanSrc()
      val content = generateAndRead(
        "src/main/scala/baklavaclient/users/Endpoints.scala",
        Seq(getCall("/users", tag = Some("Users"), operationId = Some("listUsers")))
      )
      content should include("def listUsers(")
      content should include("object UsersEndpoints {")
      content should include("package baklavaclient.users")
    }

    it("rewrites {name} path segments as Scala string interpolation against sttp `addPath`") {
      cleanSrc()
      val content = generateAndRead(
        "src/main/scala/baklavaclient/users/Endpoints.scala",
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
        new String(Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/Types.scala").toPath))
      types should include("package baklavaclient.users")
      types should include("final case class User(")
      types should include("id: Long")
      types should include("name: String")
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/common/Types.scala").exists() shouldBe false
    }

    it("puts a type used by two or more tags in common/Types.scala and imports it from each tag's Endpoints.scala") {
      cleanSrc()
      val errSchema = namedObject("ErrorResponse", Map("message" -> Schema.stringSchema))

      val callUsers = getCall("/users", tag = Some("Users"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))
      val callProjects = getCall("/projects", tag = Some("Projects"))
        .let(c => c.copy(response = c.response.copy(bodySchema = Some(errSchema))))

      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(callUsers, callProjects))

      val common =
        new String(Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/common/Types.scala").toPath))
      common should include("package baklavaclient.common")
      common should include("final case class ErrorResponse(")

      val users =
        new String(Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/Endpoints.scala").toPath))
      users should include("import baklavaclient.common.ErrorResponse")

      val projects =
        new String(Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/baklavaclient/projects/Endpoints.scala").toPath))
      projects should include("import baklavaclient.common.ErrorResponse")

      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/users/Types.scala").exists() shouldBe false
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/projects/Types.scala").exists() shouldBe false
    }

    it("honors the `sttp-client-package` config key as the emitted package") {
      cleanSrc()
      new BaklavaDslFormatterSttpClient().create(
        Map("sttp-client-package" -> "com.example.api"),
        Seq(getCall("/users", tag = Some("Users")))
      )
      val content = new String(
        Files.readAllBytes(new File("target/baklava/sttpclient/src/main/scala/com/example/api/users/Endpoints.scala").toPath)
      )
      content should include("package com.example.api.users")
    }

    it("adds a bearer token credential parameter and Authorization header for Bearer-secured endpoints") {
      cleanSrc()
      val scheme  = BaklavaSecuritySchemaSerializable("bearerAuth", BaklavaSecuritySerializable(httpBearer = Some(HttpBearer())))
      val base    = getCall("/me", tag = Some("Users"))
      val call    = base.copy(request = base.request.copy(securitySchemes = Seq(scheme)))
      val content = generateAndRead("src/main/scala/baklavaclient/users/Endpoints.scala", Seq(call))
      content should include("bearerAuthToken: String")
      content should include("""      .header("Authorization", s"Bearer ${bearerAuthToken}")""")
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
    val dir = new File("target/baklava/sttpclient/src")
    if (dir.exists()) deleteRecursively(dir)
  }

  private def deleteRecursively(f: File): Unit = {
    if (f.isDirectory) f.listFiles().foreach(deleteRecursively)
    f.delete()
    ()
  }

  private def getCall(
      path: String,
      tag: Option[String] = None,
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
