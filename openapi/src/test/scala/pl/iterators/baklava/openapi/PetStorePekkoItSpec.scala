package pl.iterators.baklava.openapi

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.stream.Materializer
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import org.specs2.specification.core.{AsExecution, Fragment, Fragments}
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.specs2.BaklavaSpecs2
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

trait PetStorePekkoItSpec
    extends SpecificationLike
    with AfterAll
    with BaklavaPekkoHttp[Fragment, Fragments, AsExecution]
    with BaklavaSpecs2[Route, ToEntityMarshaller, FromEntityUnmarshaller]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum {

  override def strictHeaderCheckDefault: Boolean = false

  private implicit val system: ActorSystem        = ActorSystem()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  val routes: Route = complete(StatusCodes.OK)

  // needed to override circe's always-JSON unmarshaller
  implicit val stringUnmarshaller: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller

  override def performRequest(routes: Route, request: HttpRequest): HttpResponse = {
    val fixedRequest = request.withUri("http://localhost:8080/api/v3" + request.uri.toString())
    Await.result(Http().singleRequest(fixedRequest), Duration.Inf)
  }
}
