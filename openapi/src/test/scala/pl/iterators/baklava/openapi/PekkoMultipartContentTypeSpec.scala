package pl.iterators.baklava.openapi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.{Multipart => PekkoMultipart}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshal}
import org.apache.pekko.stream.Materializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{AppliedSecurity, BaklavaRequestContext, FilePart, Multipart => BaklavaMultipart, NoopSecurity, TextPart}
import sttp.model.{Header => SttpHeader, Method}

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/** Companion to `Http4sMultipartContentTypeSpec` (issue #102). Pekko's `Multipart.FormData.toEntity(boundary)` bakes
  * `Content-Type: multipart/form-data; boundary=…` into the `MessageEntity` itself — unlike http4s, where the encoder leaves
  * `Headers.empty` and the adapter has to recover the header. So pekko is NOT affected by #102. This spec locks that behavior in so a
  * future marshaller change can't silently regress it.
  */
class PekkoMultipartContentTypeSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  private implicit val system: ActorSystem        = ActorSystem("pekko-multipart-content-type")
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  val routes: Route                                                              = Route.seal(_ => throw new UnsupportedOperationException)
  override def strictHeaderCheckDefault: Boolean                                 = false
  override def performRequest(routes: Route, request: HttpRequest): HttpResponse =
    throw new UnsupportedOperationException("this spec only exercises request-building, not routing")

  describe("pekko-http adapter's baklavaContextToHttpRequest with a Multipart body (issue #102 — sibling check)") {

    it("sets Content-Type: multipart/form-data with the boundary parameter") {
      val ctx     = buildMultipartRequestContext(Seq.empty)
      val request = baklavaContextToHttpRequest(ctx)

      val ct = request.entity.contentType
      ct.mediaType.mainType shouldBe "multipart"
      ct.mediaType.subType shouldBe "form-data"
      ct.mediaType.params.get("boundary") shouldBe Some("baklava-multipart-boundary")
    }

    it("produces an entity that pekko's Multipart.FormData unmarshaller accepts") {
      // Sibling to the http4s round-trip test — verifies that the resulting request can be parsed
      // back into a multipart form by pekko's own unmarshaller.
      val ctx     = buildMultipartRequestContext(Seq.empty)
      val request = baklavaContextToHttpRequest(ctx)

      val formData = Await.result(
        Unmarshal(request.entity).to[PekkoMultipart.FormData],
        Duration.Inf
      )
      val parts = Await.result(
        formData.parts.runFold(List.empty[String])((acc, p) => acc :+ p.name),
        Duration.Inf
      )
      parts shouldBe List("photo", "caption")
    }

    it("still honors a declared Content-Type override") {
      // Mirrors the http4s sibling: a declared Content-Type header on the test should win.
      val ctx     = buildMultipartRequestContext(Seq(SttpHeader("Content-Type", "image/png")))
      val request = baklavaContextToHttpRequest(ctx)

      request.entity.contentType.mediaType shouldBe MediaTypes.`image/png`
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

  override def afterAll(): Unit = { val _ = system.terminate() }
}
