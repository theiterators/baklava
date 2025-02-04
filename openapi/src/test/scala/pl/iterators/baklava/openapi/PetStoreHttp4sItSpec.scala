package pl.iterators.baklava.openapi

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{HttpRoutes, Request, Response, Uri}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.wait.strategy.Wait
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

trait PetStoreHttp4sItSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaScalatest[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller]
    with BaklavaHttp4s[Unit, Unit, ScalatestAsExecution]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum
    with TestContainerForAll {

  override val containerDef = GenericContainer.Def(
    "swaggerapi/petstore3:unstable",
    exposedPorts = Seq(8080),
    waitStrategy = Wait.forHttp("/")
  )

  override def strictHeaderCheckDefault: Boolean = false

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case _ =>
    IO(Response[IO]())
  }
  implicit val runtime: IORuntime = IORuntime.global

  def performRequest(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] = {
    withContainers { petstoreApiContainer =>
      EmberClientBuilder
        .default[IO]
        .build
        .use { client =>
          client
            .run(
              request.withUri(
                Uri.unsafeFromString(
                  s"http://${petstoreApiContainer.containerIpAddress}:${petstoreApiContainer.mappedPort(8080)}/api/v3${request.uri.toString()}"
                )
              )
            )
            .use { response =>
              // fixing for unsafeRunSync
              response.body.compile.toList.flatMap { bodyBytes =>
                IO.pure(response.copy(body = fs2.Stream.emits(bodyBytes)))
              }
            }
        }
        .unsafeRunSync()
    }
  }
}
