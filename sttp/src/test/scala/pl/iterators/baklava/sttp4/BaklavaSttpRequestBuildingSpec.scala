package pl.iterators.baklava.sttp4

import io.circe.{Decoder, Encoder}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{
  AppliedSecurity,
  BaklavaRequestContext,
  EmptyBody,
  EmptyBodyInstance,
  FormOf,
  Multipart,
  NoopSecurity,
  TextPart
}
import sttp.client4.{ByteArrayBody, NoBody, SyncBackend}
import sttp.model.{Header => SttpHeader, Method, Uri}

import java.nio.charset.StandardCharsets.UTF_8

case class Greeting(hello: String)

object Greeting {
  implicit val encoder: Encoder[Greeting] = Encoder.forProduct1("hello")(_.hello)
  implicit val decoder: Decoder[Greeting] = Decoder.forProduct1("hello")(Greeting.apply)
}

class BaklavaSttpRequestBuildingSpec
    extends AnyFunSpec
    with Matchers
    with org.scalatest.Inside
    with BaklavaSttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[SyncBackend, ToSttpBody, FromSttpBody] {

  override def baseUri: Uri                    = Uri.unsafeParse("https://api.example.com")
  override def defaultHeaders: Seq[SttpHeader] = Seq(SttpHeader("X-Default", "on"), SttpHeader("X-Both", "default"))

  describe("resolveUri") {
    it("appends the context path to the base URI") {
      BaklavaSttp.resolveUri(Uri.unsafeParse("https://api.example.com"), "/x?y=1").toString shouldBe
      "https://api.example.com/x?y=1"
    }

    it("tolerates a trailing slash on the base URI") {
      BaklavaSttp.resolveUri(Uri.unsafeParse("https://api.example.com/"), "/x").toString shouldBe
      "https://api.example.com/x"
    }

    it("preserves a path prefix on the base URI") {
      BaklavaSttp.resolveUri(Uri.unsafeParse("https://api.example.com/api/v3"), "/pets").toString shouldBe
      "https://api.example.com/api/v3/pets"
    }

    it("rejects a base URI carrying a query or fragment") {
      val ex = intercept[IllegalArgumentException](BaklavaSttp.resolveUri(Uri.unsafeParse("https://api.example.com?key=x"), "/pets"))
      ex.getMessage should include("must not carry a query or fragment")
    }
  }

  describe("baklavaContextToHttpRequest") {
    it("builds method and URI from the context") {
      val request = baklavaContextToHttpRequest(buildRequestContext[String](Seq.empty, None, path = "/pets?limit=10"))
      request.method shouldBe Method.GET
      request.uri.toString shouldBe "https://api.example.com/pets?limit=10"
    }

    it("adds default headers and lets declared headers win on name conflict") {
      val request = baklavaContextToHttpRequest(buildRequestContext[String](Seq(SttpHeader("X-Both", "declared")), None))
      request.headers.filter(_.name == "X-Both").map(_.value) shouldBe Seq("declared")
      request.headers.find(_.name == "X-Default").map(_.value) shouldBe Some("on")
    }

    it("serializes a JSON body via a circe Encoder") {
      val request = baklavaContextToHttpRequest(buildRequestContext(Seq.empty, Some(Greeting("hi"))))
      inside(request.body) { case ByteArrayBody(bytes, _) => new String(bytes, UTF_8) shouldBe """{"hello":"hi"}""" }
      contentTypesOf(request) shouldBe Seq("application/json")
    }

    it("serializes a String body as text/plain") {
      val request = baklavaContextToHttpRequest(buildRequestContext(Seq.empty, Some("raw text")))
      inside(request.body) { case ByteArrayBody(bytes, _) => new String(bytes, UTF_8) shouldBe "raw text" }
      contentTypesOf(request) shouldBe Seq("text/plain; charset=UTF-8")
    }

    it("serializes a FormOf body as application/x-www-form-urlencoded") {
      val request = baklavaContextToHttpRequest(buildRequestContext(Seq.empty, Some(FormOf[Greeting]("hello" -> "world"))))
      inside(request.body) { case ByteArrayBody(bytes, _) => new String(bytes, UTF_8) shouldBe "hello=world" }
      contentTypesOf(request) shouldBe Seq("application/x-www-form-urlencoded")
    }

    it("serializes a Multipart body with the fixed boundary") {
      val multipart = Multipart(TextPart("a", "b"))
      val request   = baklavaContextToHttpRequest(buildRequestContext(Seq.empty, Some(multipart)))
      inside(request.body) { case ByteArrayBody(bytes, _) => bytes shouldBe SttpBodies.renderMultipart(multipart) }
      contentTypesOf(request) shouldBe Seq(SttpBodies.multipartContentType)
    }

    it("sends no body and no Content-Type for EmptyBody") {
      val request = baklavaContextToHttpRequest(buildRequestContext[EmptyBody](Seq.empty, Some(EmptyBodyInstance)))
      request.body shouldBe NoBody
      contentTypesOf(request) shouldBe Seq.empty
    }

    it("overrides the body's Content-Type when one is declared among the headers") {
      val request = baklavaContextToHttpRequest(buildRequestContext(Seq(SttpHeader("Content-Type", "image/png")), Some("raw bytes")))
      contentTypesOf(request) shouldBe Seq("image/png")
    }

    it("throws on an unparseable declared Content-Type") {
      val ctx = buildRequestContext(Seq(SttpHeader("Content-Type", "this is not a content type")), Some("irrelevant"))
      val ex  = intercept[IllegalArgumentException](baklavaContextToHttpRequest(ctx))
      ex.getMessage should include("Could not parse declared Content-Type")
    }

    it("throws on multiple declared Content-Type headers") {
      val ctx = buildRequestContext(Seq(SttpHeader("Content-Type", "image/png"), SttpHeader("content-type", "image/jpeg")), Some("x"))
      val ex  = intercept[IllegalArgumentException](baklavaContextToHttpRequest(ctx))
      ex.getMessage should include("Multiple Content-Type headers")
    }
  }

  private def contentTypesOf(request: HttpRequest): Seq[String] =
    request.headers.filter(_.name.equalsIgnoreCase("Content-Type")).map(_.value)

  // Everything except headers/body/path is boilerplate the adapter ignores.
  private def buildRequestContext[B](
      hs: Seq[SttpHeader],
      body: Option[B],
      path: String = "/x"
  ): BaklavaRequestContext[B, Unit, Unit, Unit, Unit, Unit, Unit] =
    BaklavaRequestContext[B, Unit, Unit, Unit, Unit, Unit, Unit](
      symbolicPath = path,
      path = path,
      pathDescription = None,
      pathSummary = None,
      method = Some(if (body.isDefined) Method.POST else Method.GET),
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
      securitySchemes = Seq.empty,
      body = body,
      bodySchema = None,
      headers = hs,
      headersDefinition = (),
      headersProvided = (),
      headersSeq = Seq.empty,
      security = AppliedSecurity(NoopSecurity, Map.empty),
      pathParameters = (),
      pathParametersProvided = (),
      pathParametersSeq = Seq.empty,
      queryParameters = (),
      queryParametersProvided = (),
      queryParametersSeq = Seq.empty,
      responseDescription = None,
      responseHeaders = Seq.empty
    )

  override def afterAll(): Unit = ()
}
