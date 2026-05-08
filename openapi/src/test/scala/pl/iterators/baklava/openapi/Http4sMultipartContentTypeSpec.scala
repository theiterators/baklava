package pl.iterators.baklava.openapi

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.headers.`Content-Type`
import org.http4s.multipart.{Multipart => Http4sMultipart}
import org.http4s.{EntityDecoder, HttpRoutes, MediaType, Request, Response, Status}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{AppliedSecurity, BaklavaRequestContext, FilePart, Multipart => BaklavaMultipart, NoopSecurity, TextPart}
import sttp.model.{Header => SttpHeader, Method}

import java.nio.charset.StandardCharsets

// Regression for issue #102: http4s' MultipartEncoder leaves Content-Type unset, so the adapter
// has to recover it from the Multipart value itself.
class Http4sMultipartContentTypeSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaHttp4s[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller] {

  override implicit val runtime: IORuntime                                                = IORuntime.global
  override def strictHeaderCheckDefault: Boolean                                          = false
  override def performRequest(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] =
    Response[IO](status = Status.NoContent)

  val routes: HttpRoutes[IO] = HttpRoutes.empty[IO]

  describe("http4s adapter's baklavaContextToHttpRequest with a Multipart body (issue #102)") {

    it("sets Content-Type: multipart/form-data with the boundary parameter") {
      val ctx     = buildMultipartRequestContext(Seq.empty)
      val request = baklavaContextToHttpRequest(ctx)(multipartToRequestBodyType)

      val ct = request.contentType
      ct.map(_.mediaType.mainType) shouldBe Some("multipart")
      ct.map(_.mediaType.subType) shouldBe Some("form-data")
      ct.flatMap(_.mediaType.extensions.get("boundary")) shouldBe Some("baklava-multipart-boundary")
    }

    it("still honors a declared Content-Type override") {
      // Preserves issue #52 override behavior on top of the multipart fix.
      val ctx     = buildMultipartRequestContext(Seq(SttpHeader("Content-Type", "image/png")))
      val request = baklavaContextToHttpRequest(ctx)(multipartToRequestBodyType)

      request.contentType shouldBe Some(`Content-Type`(MediaType.image.png))
    }

    it("uses one Multipart value for both the body and the Content-Type so boundaries can't diverge") {
      // The boundary on the Content-Type and the boundary marker in the body bytes must match.
      val ctx     = buildMultipartRequestContext(Seq.empty)
      val request = baklavaContextToHttpRequest(ctx)(multipartToRequestBodyType)

      val advertisedBoundary = request.contentType
        .flatMap(_.mediaType.extensions.get("boundary"))
        .getOrElse(
          fail("expected a boundary parameter on the Content-Type header")
        )
      val bodyString = new String(request.body.compile.toVector.unsafeRunSync().toArray, StandardCharsets.UTF_8)
      bodyString should include(s"--$advertisedBoundary")
    }

    it("produces a request that http4s' Multipart decoder accepts") {
      // Reproduces the exact failure mode from issue #102.
      val ctx     = buildMultipartRequestContext(Seq.empty)
      val request = baklavaContextToHttpRequest(ctx)(multipartToRequestBodyType)

      val decoder: EntityDecoder[IO, Http4sMultipart[IO]] = EntityDecoder.multipart[IO]
      val decoded                                         = request.as[Http4sMultipart[IO]](implicitly, decoder).unsafeRunSync()

      decoded.parts.map(_.name).toList.flatten shouldBe List("photo", "caption")
    }

    it("leaves the marshaller-provided Content-Type intact on non-multipart bodies") {
      val ctx     = buildStringRequestContext(Seq.empty, "plain text")
      val request = baklavaContextToHttpRequest(ctx)(implicitly)

      // Main type only — subtype/charset varies across http4s versions.
      request.contentType.map(_.mediaType.mainType) shouldBe Some("text")
    }
  }

  private def buildMultipartRequestContext(
      hs: Seq[SttpHeader]
  ): BaklavaRequestContext[BaklavaMultipart, Unit, Unit, Unit, Unit, Unit, Unit] =
    BaklavaRequestContext[BaklavaMultipart, Unit, Unit, Unit, Unit, Unit, Unit](
      symbolicPath = "/upload",
      path = "/upload",
      pathDescription = None,
      pathSummary = None,
      method = Some(Method("POST")),
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
      securitySchemes = Seq.empty,
      body = Some(
        BaklavaMultipart(
          FilePart("photo", "image/png", "photo.png", "fake png bytes".getBytes(StandardCharsets.UTF_8)),
          TextPart("caption", "profile photo")
        )
      ),
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

  private def buildStringRequestContext(
      hs: Seq[SttpHeader],
      body: String
  ): BaklavaRequestContext[String, Unit, Unit, Unit, Unit, Unit, Unit] =
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
