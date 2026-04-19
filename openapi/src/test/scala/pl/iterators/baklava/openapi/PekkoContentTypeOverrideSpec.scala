package pl.iterators.baklava.openapi

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{AppliedSecurity, BaklavaRequestContext, NoopSecurity}
import sttp.model.{Header => SttpHeader, Method}

import scala.concurrent.ExecutionContext

/** Regression test for issue #52 on the pekko-http adapter. Parallel to `Http4sContentTypeOverrideSpec`; both adapters have their own
  * independent `baklavaContextToHttpRequest` that needs to honor a declared `Content-Type` header (and reject duplicates / unparseable
  * values).
  */
class PekkoContentTypeOverrideSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  private implicit val system: ActorSystem        = ActorSystem("pekko-content-type-override")
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  val routes: Route                                                              = Route.seal(_ => throw new UnsupportedOperationException)
  override def strictHeaderCheckDefault: Boolean                                 = false
  override def performRequest(routes: Route, request: HttpRequest): HttpResponse =
    throw new UnsupportedOperationException("this spec only exercises request-building, not routing")

  describe("pekko-http adapter's baklavaContextToHttpRequest (issue #52)") {

    it("overrides the entity Content-Type when one is declared among the headers") {
      val ctx     = buildRequestContext(Seq(SttpHeader("Content-Type", "image/png")), "raw bytes")
      val request = baklavaContextToHttpRequest(ctx)

      request.entity.contentType.mediaType shouldBe MediaTypes.`image/png`
    }

    it("does not leave a duplicate Content-Type in the free header list") {
      val ctx     = buildRequestContext(Seq(SttpHeader("Content-Type", "image/png")), "raw bytes")
      val request = baklavaContextToHttpRequest(ctx)

      val ctHeaders = request.headers.filter(_.is("content-type"))
      ctHeaders shouldBe empty
      request.entity.contentType.mediaType shouldBe MediaTypes.`image/png`
    }

    it("leaves the marshaller-provided Content-Type intact when none is declared") {
      val ctx     = buildRequestContext(Seq.empty, "plain text")
      val request = baklavaContextToHttpRequest(ctx)

      request.entity.contentType.mediaType.mainType shouldBe "text"
    }

    it("throws on an unparseable declared Content-Type — silent fallback would hide a bug") {
      val ctx = buildRequestContext(Seq(SttpHeader("Content-Type", "this is not a content type")), "irrelevant")
      val ex  = intercept[IllegalArgumentException](baklavaContextToHttpRequest(ctx))
      ex.getMessage should include("Could not parse declared Content-Type")
    }

    it("throws on multiple declared Content-Type headers") {
      val ctx = buildRequestContext(
        Seq(SttpHeader("Content-Type", "image/png"), SttpHeader("content-type", "image/jpeg")),
        "bytes"
      )
      val ex = intercept[IllegalArgumentException](baklavaContextToHttpRequest(ctx))
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

  override def afterAll(): Unit = { val _ = system.terminate() }
}
