---
sidebar_position: 3
title: Pekko HTTP Integration
---

# Pekko HTTP Integration

Baklava provides seamless integration with Pekko HTTP server, route documentation in unit tests and serving Open API via Swagger UI.

## Documenting routes in unit tests

### Spec2

Most convenient way to use Baklava with Spec2 is to define base class for all tests. This can look like below:
```scala
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.Specs2RouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.core.{AsExecution, Fragment, Fragments}
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.specs2.BaklavaSpecs2

trait BaseRouteSpec
    extends Specs2RouteTest
    with SpecificationLike
    with BaklavaPekkoHttp[Fragment, Fragments, AsExecution]
    with BaklavaSpecs2[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  // Define the routes to test
  def allRoutes: Route = ??? // All application routes

  // Required implementations for Baklava framework
  override implicit val executionContext: scala.concurrent.ExecutionContext =
    system.dispatcher

  override def strictHeaderCheckDefault: Boolean = false

  override def performRequest(
      routes: Route,
      request: HttpRequest
  ): HttpResponse =
    request ~> routes ~> check {
      response
    }
}
```

Then use above as base class for your tests as below:

```scala
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import pl.iterators.example.baklava.UserApiServer.*

class GetUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user"
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[User](OK, description = "Return user with ID 1")
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
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.RouteTestTimeout
import org.apache.pekko.http.scaladsl.testkit.munit.MunitRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import pl.iterators.baklava.munit.{BaklavaMunit, MunitAsExecution}
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp

trait BaseRouteTest
    extends MunitRouteTest
    with BaklavaPekkoHttp[Unit, Unit, MunitAsExecution]
    with BaklavaMunit[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  // Define the routes to test
  def allRoutes: Route = ??? // All application routes

  // Required implementations for Baklava framework
  override implicit val executionContext: scala.concurrent.ExecutionContext =
    system.dispatcher

  override def strictHeaderCheckDefault: Boolean = false

  override def performRequest(
      routes: Route,
      request: HttpRequest
  ): HttpResponse =
    request ~> routes ~> check {
      response
    }
}
```

Then use above as base class for your tests as below:

```scala
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import pl.iterators.example.baklava.UserApiServer.*

class GetUsersUserIdRouteTest extends BaseRouteTest {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user"
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[User](OK, description = "Return user with ID 1")
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
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.example.baklava.UserApiServer

trait BaseRouteSpec
    extends AnyFunSpec
    with ScalatestRouteTest
    with Matchers
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  // Define the routes to test
  def allRoutes: Route = UserApiServer.userRoutes

  // Required implementations for Baklava framework
  override implicit val executionContext: scala.concurrent.ExecutionContext =
    system.dispatcher

  override def strictHeaderCheckDefault: Boolean = false

  override def performRequest(
      routes: Route,
      request: HttpRequest
  ): HttpResponse =
    request ~> routes ~> check {
      response
    }
}
```

Then use above as base class for your tests as below:

```scala
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import pl.iterators.example.baklava.UserApiServer.*

class GetUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user"
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[User](OK, description = "Return user with ID 1")
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

## Serving Open API and Swagger UI

Adding `baklava-pekko-http-routes` dependency to your project you can easily serve Open API and Swagger UI:

```scala
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.server.Route
import com.typesafe.config.Config
import pl.iterators.baklava.routes.BaklavaRoutes

import scala.concurrent.ExecutionContext
import scala.io.StdIn

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "api-actor-system")
    implicit val executionContext: ExecutionContext = system.executionContext
    val config: Config = system.settings.config

    val apiRoute: Route = ??? // all your api routes
    val apiAndSwaggerRoute: Route = apiRoute ~ BaklavaRoutes.routes(config)

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(apiAndSwaggerRoute)

    println(s"Server online at http://localhost:8080/")
    println("Press RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
```

For detailed configuration options check [installation.md#swaggerui-and-routes-configuration]
