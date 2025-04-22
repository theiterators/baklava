package pl.iterators.baklava.openapi

import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.stream.Materializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.wait.strategy.Wait
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.ScalatestAsExecution
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

trait PetStorePekkoItSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatestDebug[Route, ToEntityMarshaller, FromEntityUnmarshaller]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum
    with TestContainerForAll {

  override val containerDef: GenericContainer.Def[GenericContainer] = GenericContainer.Def(
    "swaggerapi/petstore3:unstable",
    exposedPorts = Seq(8080),
    waitStrategy = Wait.forHttp("/")
  )

  override def strictHeaderCheckDefault: Boolean = false

  private implicit val system: ActorSystem        = ActorSystem()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  val routes: Route = complete(StatusCodes.OK)

  // needed to override circe's always-JSON unmarshaller
  implicit val stringUnmarshaller: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller

  override def performRequest(routes: Route, request: HttpRequest): HttpResponse = {
    withContainers { petstoreApiContainer =>
      val fixedRequest = request.withUri(
        s"http://${petstoreApiContainer.containerIpAddress}:${petstoreApiContainer.mappedPort(8080)}/api/v3" + request.uri.toString()
      )
      Await.result(Http().singleRequest(fixedRequest), Duration.Inf)
    }
  }
}
