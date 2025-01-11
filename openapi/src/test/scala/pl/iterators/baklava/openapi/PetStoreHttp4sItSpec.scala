package pl.iterators.baklava.openapi

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{HttpRoutes, Request, Response, Uri}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import org.specs2.specification.core.{AsExecution, Fragment, Fragments}
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.specs2.BaklavaSpecs2
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

trait PetStoreHttp4sItSpec
    extends SpecificationLike
    with AfterAll
    with BaklavaHttp4s[Fragment, Fragments, AsExecution]
    with BaklavaSpecs2[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum {

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] { case _ =>
    IO(Response[IO]())
  }
  implicit val runtime: IORuntime = IORuntime.global

  def performRequest(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] = {
    EmberClientBuilder
      .default[IO]
      .build
      .use { client =>
        client
          .run(request.withUri(Uri.unsafeFromString(s"http://localhost:8080/api/v3${request.uri.toString()}")))
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
