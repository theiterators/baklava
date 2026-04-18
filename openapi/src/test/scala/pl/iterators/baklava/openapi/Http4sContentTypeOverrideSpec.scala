package pl.iterators.baklava.openapi

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{EntityEncoder, HttpRoutes, MediaType, Request, Response, Status}
import org.http4s.headers.`Content-Type`
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{AppliedSecurity, BaklavaRequestContext, NoopSecurity}
import sttp.model.{Header => SttpHeader, Method}

/** Regression test for issue #52 on the http4s adapter. The pekko-http adapter already gets the same coverage via ComprehensiveGoldSpec's
  * `/users/{userId}/avatar` endpoint; this spec exists because the http4s adapter has its own independent `baklavaContextToHttpRequest`
  * that also needs to honor a declared `Content-Type` header and strip duplicates.
  */
class Http4sContentTypeOverrideSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaHttp4s[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller] {

  override implicit val runtime: IORuntime                                                = IORuntime.global
  override def strictHeaderCheckDefault: Boolean                                          = false
  override def performRequest(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] =
    Response[IO](status = Status.NoContent)

  val routes: HttpRoutes[IO]                       = HttpRoutes.empty[IO]
  private val stringEnc: EntityEncoder[IO, String] = EntityEncoder.stringEncoder[IO]

  describe("http4s adapter's baklavaContextToHttpRequest (issue #52)") {

    it("overrides the entity Content-Type when one is declared among the headers") {
      val ctx     = buildRequestContext(Seq(SttpHeader("Content-Type", "image/png")), "raw bytes")
      val request = baklavaContextToHttpRequest(ctx)(stringEnc)

      request.contentType shouldBe Some(`Content-Type`(MediaType.image.png))
    }

    it("does not leave a duplicate Content-Type in the free header list") {
      val ctx     = buildRequestContext(Seq(SttpHeader("Content-Type", "image/png")), "raw bytes")
      val request = baklavaContextToHttpRequest(ctx)(stringEnc)

      // http4s stores Content-Type on the entity, not in the free header list. The declared
      // Content-Type must appear exactly once — via the entity — not as an extra header.
      val contentTypeHeaders =
        request.headers.headers.filter(_.name.toString.equalsIgnoreCase("Content-Type"))
      contentTypeHeaders.size shouldBe 1
      contentTypeHeaders.head.value shouldBe "image/png"
    }

    it("leaves the marshaller-provided Content-Type intact when none is declared") {
      val ctx     = buildRequestContext(Seq.empty, "plain text")
      val request = baklavaContextToHttpRequest(ctx)(stringEnc)

      // http4s's String EntityEncoder defaults to text/plain; assert the main type without
      // pinning the exact subtype/charset which is less stable across http4s versions.
      request.contentType.map(_.mediaType.mainType) shouldBe Some("text")
    }

    it("throws on an unparseable declared Content-Type — silent fallback would hide a bug") {
      val ctx = buildRequestContext(Seq(SttpHeader("Content-Type", "this is not a content type")), "irrelevant")
      val ex  = intercept[IllegalArgumentException](baklavaContextToHttpRequest(ctx)(stringEnc))
      ex.getMessage should include("Could not parse declared Content-Type")
    }

    it("throws on multiple declared Content-Type headers") {
      val ctx = buildRequestContext(
        Seq(SttpHeader("Content-Type", "image/png"), SttpHeader("content-type", "image/jpeg")),
        "bytes"
      )
      val ex = intercept[IllegalArgumentException](baklavaContextToHttpRequest(ctx)(stringEnc))
      ex.getMessage should include("Multiple Content-Type headers")
    }
  }

  private def buildRequestContext(hs: Seq[SttpHeader], body: String): BaklavaRequestContext[String, Unit, Unit, Unit, Unit, Unit, Unit] =
    BaklavaRequestContext[String, Unit, Unit, Unit, Unit, Unit, Unit](
      symbolicPath = "/x",
      path = "/x",
      pathDescription = None,
      pathSummary = None,
      method = Some(Method("POST")),
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
      securitySchemes = Seq.empty,
      body = Some(body),
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
