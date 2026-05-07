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

/** Regression test for issue #102 on the http4s adapter. http4s' `MultipartEncoder` carries an empty header set, so a vanilla
  * `withEntity(multipart)` produces a request with no `Content-Type: multipart/form-data; boundary=…`. The adapter has to recover those
  * headers from the http4s `Multipart` value itself; otherwise routes decoding `Multipart[IO]` reject with
  * `Missing boundary extension to Content-Type`.
  */
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
      // If the user explicitly declares a Content-Type header, it wins over the encoder-derived
      // one — preserving the existing override behavior from issue #52.
      val ctx     = buildMultipartRequestContext(Seq(SttpHeader("Content-Type", "image/png")))
      val request = baklavaContextToHttpRequest(ctx)(multipartToRequestBodyType)

      request.contentType shouldBe Some(`Content-Type`(MediaType.image.png))
    }

    it("uses one Multipart value for both the body and the Content-Type so boundaries can't diverge") {
      // Lock in the same-Multipart invariant: the boundary on the advertised Content-Type and
      // the boundary marker inside the encoded body bytes must match. If a future refactor goes
      // back to building the http4s Multipart twice (once for the entity, once for headers),
      // a consumer override of `toHttp4sMultipart` could let the two diverge — this assertion
      // catches that by reading the boundary parameter from the header and grepping the body.
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
      // This is the exact failure mode reported in issue #102: an http4s route doing
      // `entity(as[Multipart[IO]])` previously rejected the request with
      // `Missing boundary extension to Content-Type`. After the fix, decoding succeeds.
      val ctx     = buildMultipartRequestContext(Seq.empty)
      val request = baklavaContextToHttpRequest(ctx)(multipartToRequestBodyType)

      val decoder: EntityDecoder[IO, Http4sMultipart[IO]] = EntityDecoder.multipart[IO]
      val decoded                                         = request.as[Http4sMultipart[IO]](implicitly, decoder).unsafeRunSync()

      decoded.parts.map(_.name).toList.flatten shouldBe List("photo", "caption")
    }

    it("does not put a Content-Type on requests with a non-multipart body") {
      // Sanity check that the multipart-only branch does not affect ordinary bodies.
      val ctx     = buildStringRequestContext(Seq.empty, "plain text")
      val request = baklavaContextToHttpRequest(ctx)(implicitly)

      // String EntityEncoder defaults to text/plain; assert main type, not exact subtype/charset.
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
