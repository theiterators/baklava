---
sidebar_position: 4
title: HTTP4s Integration
---

# HTTP4s Integration

Baklava provides seamless integration with http4s server and route documentation in unit tests.

## Documenting routes in unit tests

### Spec2

Most convenient way to use Baklava with Spec2 is to define base class for all tests. This can look like below:
```scala
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import cats.effect.testing.specs2.CatsEffect
import org.http4s.{HttpRoutes, Request}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.core.{AsExecution, Fragment, Fragments}
import pl.iterators.baklava.specs2.BaklavaSpecs2
import pl.iterators.baklava.http4s.BaklavaHttp4s

import com.example.users.UserRoutes

trait BaseRouteSpec
    extends CatsEffect
    with SpecificationLike
    with BaklavaHttp4s[Fragment, Fragments, AsExecution]
    with BaklavaSpecs2[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller] {

  // Define the routes to test
  val allRoutes: HttpRoutes[IO] = ???

  // Required implementations for Baklava framework
  override implicit val runtime: IORuntime = IORuntime.global

  override def strictHeaderCheckDefault: Boolean = false

  override def performRequest(routes: HttpRoutes[IO], request: Request[IO]): HttpResponse =
    routes.orNotFound.run(request).unsafeRunSync()

  override def afterAll(): Unit = ()

}
```

Then use above as base class for your tests as below:
```scala
import io.circe.generic.auto.*
import org.http4s.Method.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*

class GetUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user"
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[User](Ok, description = "Return user with ID 1")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.id should beEqualTo(1L)
        },
      onRequest(pathParameters = (999L))
        .respondsWith[ErrorResponse](NotFound, description = "Return 404 for non-existent user")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body should beEqualTo {
            ErrorResponse("User with the specified ID does not exist", "USER_NOT_FOUND")
          }
        }
    )
  )

}
```

### MUnit

Most convenient way to use Baklava with MUnit is to define base class for all tests. This can look like below:
```scala
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import munit.CatsEffectSuite
import org.http4s.{HttpRoutes, Request}
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.munit.{BaklavaMunit, MunitAsExecution}

trait BaseRouteTest
    extends CatsEffectSuite
    with BaklavaHttp4s[Unit, Unit, MunitAsExecution]
    with BaklavaMunit[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller] {

  // Define the routes to test
  val allRoutes: HttpRoutes[IO] = ???

  // Required implementations for Baklava framework
  override implicit val runtime: IORuntime = IORuntime.global

  override def strictHeaderCheckDefault: Boolean = false

  override def performRequest(routes: HttpRoutes[IO], request: Request[IO]): HttpResponse =
    routes.orNotFound.run(request).unsafeRunSync()

}
```

Then use above as base class for your tests as below:
```scala
import io.circe.generic.auto.*
import org.http4s.Method.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*

class GetUsersUserIdRouteTest extends BaseRouteTest {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user"
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[User](Ok, description = "Return user with ID 1")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          assertEquals(response.body.id, 1L)
        },
      onRequest(pathParameters = (999L))
        .respondsWith[ErrorResponse](NotFound, description = "Return 404 for non-existent user")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          assertEquals(
            response.body,
            ErrorResponse("User with the specified ID does not exist", "USER_NOT_FOUND")
          )
        }
    )
  )

}
```

### ScalaTest

Most convenient way to use Baklava with ScalaTest is to define base class for all tests. This can look like below:
```scala
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{HttpRoutes, Request}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}

import com.example.users.UserRoutes

trait BaseRouteSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaHttp4s[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller] {

  // Define the routes to test
  val allRoutes: HttpRoutes[IO] = UserRoutes.routes

  // Required implementations for Baklava framework
  override implicit val runtime: IORuntime = IORuntime.global

  override def strictHeaderCheckDefault: Boolean = false

  override def performRequest(routes: HttpRoutes[IO], request: Request[IO]): HttpResponse =
    routes.orNotFound.run(request).unsafeRunSync()

}
```

Then use above as base class for your tests as below:
```scala
import io.circe.generic.auto.*
import org.http4s.Method.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*

class GetUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user"
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[User](Ok, description = "Return user with ID 1")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.id shouldBe 1L
        },
      onRequest(pathParameters = (999L))
        .respondsWith[ErrorResponse](NotFound, description = "Return 404 for non-existent user")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body shouldBe ErrorResponse("User with the specified ID does not exist", "USER_NOT_FOUND")
        }
    )
  )

}
```

## Documenting file uploads

To document a binary upload (e.g. an avatar PNG), declare `Content-Type` among the request headers and pass the matching value on the `onRequest(...)` call — the http4s adapter honors that declared value, overriding the content type the `EntityEncoder` bakes into the request.

```scala
import java.nio.charset.StandardCharsets

class PutUsersUserIdAvatarSpec extends BaseRouteSpec {

  // Byte-array entity encoders ship with http4s; no extra setup needed.

  path(path = "/users/{userId}/avatar")(
    supports(
      PUT,
      pathParameters = p[Long]("userId"),
      headers = h[String]("Content-Type"),
      description = "Upload or update a user's avatar",
      summary = "Upload or update a user's avatar",
      tags = List("Users")
    )(
      onRequest(
        pathParameters = 1L,
        headers = "image/png",
        body = "\u0089PNG\r\n...".getBytes(StandardCharsets.UTF_8)
      ).respondsWith[EmptyBody](NoContent, description = "User avatar updated successfully")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)
          response.status.status should beEqualTo(NoContent.status)
        }
    )
  )
}
```

The generator renders this as `requestBody.content["image/png"]` with a `schema: { type: string, format: binary }` in OpenAPI, and the test request goes out with `Content-Type: image/png` on the wire so server routes that pattern-match on it run under the right conditions.

### Downloads

Binary downloads work with `respondsWith[Array[Byte]]`. http4s ships entity decoders for byte arrays; no extra setup needed. The test stub's response must carry the right `Content-Type` — whatever the server serves becomes the OpenAPI `responseContentType`:

```scala
supports(
  GET,
  pathParameters = p[Long]("userId"),
  description = "Download the user's avatar as raw image bytes",
  tags = List("Users")
)(
  onRequest(pathParameters = 1L)
    .respondsWith[Array[Byte]](Ok, description = "Avatar bytes")
    .assert { ctx =>
      val response = ctx.performRequest(allRoutes)
      response.status.status should beEqualTo(Ok.status)
    }
)
```

`Schema[Array[Byte]]` is a default on the classpath, so the generated OpenAPI renders `responses[code].content["<content-type>"].schema = { type: string, format: binary }`.
