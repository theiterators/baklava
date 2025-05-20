package pl.iterators.baklava.openapi

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.model.StatusCodes.NoContent
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.EmptyBody
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import scala.concurrent.ExecutionContext

class NoContentSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum {

  private implicit val system: ActorSystem        = ActorSystem()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  def performRequest(routes: Route, request: HttpRequest): HttpResponse = HttpResponse(NoContent)

  def strictHeaderCheckDefault: Boolean = true

  val routes: Route = complete(StatusCodes.OK)

  case class MyRequest(id: Int, name: String)

  path("/reproducing-bug") {
    supports(
      GET,
      summary = "Reproducing bug",
      description = "Should return 204 No Content",
      operationId = "reproduceBug",
      tags = Seq("was-bug")
    )(
      onRequest(body = MyRequest(0, "Luke")).respondsWith[EmptyBody](NoContent, description = "successful operation").assert { ctx =>
        ctx.performRequest(routes)
      }
    )
  }
}
