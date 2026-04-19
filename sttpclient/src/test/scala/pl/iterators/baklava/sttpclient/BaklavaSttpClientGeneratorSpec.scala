package pl.iterators.baklava.sttpclient

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import java.io.File
import java.nio.file.Files

class BaklavaSttpClientGeneratorSpec extends AnyFunSpec with Matchers {

  describe("BaklavaDslFormatterSttpClient") {

    it("writes the Types.scala + one {Tag}Endpoints.scala file per tag") {
      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(getCall("/users", tag = Some("Users"))))
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/Types.scala").exists() shouldBe true
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/UsersEndpoints.scala").exists() shouldBe true
      new File("target/baklava/sttpclient/README.md").exists() shouldBe true
    }

    it("puts untagged operations in DefaultEndpoints.scala") {
      new BaklavaDslFormatterSttpClient().create(Map.empty, Seq(getCall("/health", tag = None)))
      new File("target/baklava/sttpclient/src/main/scala/baklavaclient/DefaultEndpoints.scala").exists() shouldBe true
    }

    it("emits one `def` per endpoint, named from operationId when present") {
      val content = generateAndRead(
        "src/main/scala/baklavaclient/UsersEndpoints.scala",
        Seq(getCall("/users", tag = Some("Users"), operationId = Some("listUsers")))
      )
      content should include("def listUsers(")
      content should include("object UsersEndpoints {")
    }

    it("rewrites {name} path segments as Scala string interpolation against sttp `addPath`") {
      val content = generateAndRead(
        "src/main/scala/baklavaclient/UsersEndpoints.scala",
        Seq(getCall("/users/{userId}", tag = Some("Users"), pathParams = Seq("userId")))
      )
      content should include("""baseUri.addPath("users", "$userId")""")
      content should include("userId: String")
    }

    it("emits case classes in Types.scala for named object schemas") {
      val userSchema = BaklavaSchemaSerializable(
        className = "User",
        `type` = SchemaType.ObjectType,
        format = None,
        properties = Map(
          "id"   -> BaklavaSchemaSerializable(Schema.longSchema),
          "name" -> BaklavaSchemaSerializable(Schema.stringSchema)
        ),
        items = None,
        `enum` = None,
        required = true,
        additionalProperties = false,
        default = None,
        description = None
      )
      val base  = getCall("/users", tag = Some("Users"))
      val call  = base.copy(response = base.response.copy(bodySchema = Some(userSchema)))
      val types = generateAndRead("src/main/scala/baklavaclient/Types.scala", Seq(call))
      types should include("final case class User(")
      types should include("id: Long")
      types should include("name: String")
    }

    it("honors the `sttp-client-package` config key as the emitted package") {
      val content = generateAndRead(
        "src/main/scala/baklavaclient/UsersEndpoints.scala",
        Seq(getCall("/users", tag = Some("Users"))),
        Map("sttp-client-package" -> "com.example.api")
      )
      // Package in generated source reflects the config (but file path stays the same for tests).
      content should include("package com.example.api")
    }

    it("adds a bearer token credential parameter and Authorization header for Bearer-secured endpoints") {
      val scheme = BaklavaSecuritySchemaSerializable(
        "bearerAuth",
        BaklavaSecuritySerializable(httpBearer = Some(HttpBearer()))
      )
      val base    = getCall("/me", tag = Some("Users"))
      val call    = base.copy(request = base.request.copy(securitySchemes = Seq(scheme)))
      val content = generateAndRead("src/main/scala/baklavaclient/UsersEndpoints.scala", Seq(call))
      content should include("bearerAuthToken: String")
      content should include("""      .header("Authorization", s"Bearer ${bearerAuthToken}")""")
    }
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
