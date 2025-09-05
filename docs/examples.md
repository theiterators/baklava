---
sidebar_position: 8
title: Examples
---

# Examples

This section provides comprehensive examples of using Baklava to document different types of API endpoints. We use Pekko HTTP and Spec2 here.

## Basic CRUD Operations

### Base test class

```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.Specs2RouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.core.AsExecution
import org.specs2.specification.core.Fragment
import org.specs2.specification.core.Fragments
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.specs2.BaklavaSpecs2

trait BaseRouteSpec
    extends Specs2RouteTest
    with SpecificationLike
    with BaklavaPekkoHttp[Fragment, Fragments, AsExecution]
    with BaklavaSpecs2[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  // Define the routes to test
  def allRoutes: Route = UserApiServer.userRoutes

  // Required implementations for Baklava framework
  implicit val executionContext: scala.concurrent.ExecutionContext =
    system.dispatcher

  def strictHeaderCheckDefault: Boolean = false

  override def performRequest(
      routes: Route,
      request: HttpRequest
  ): HttpResponse =
    request ~> routes ~> check {
      response
    }
}
```
### List of users

```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.specs2.specification.core.AsExecution
import pl.iterators.example.baklava.UserApiServer.*

class GetUsersRouteSpec extends BaseRouteSpec {

  path(path = "/users")(
    supports(
      GET,
      description = "Get all users with optional pagination and search",
      summary = "Retrieve a list of users",
      queryParameters = (
        q[Option[Int]]("page"),
        q[Option[Int]]("limit"),
        q[Option[String]]("search")
      ),
      tags = List("Users")
    )(
      onRequest(queryParameters = (None, None, None))
        .respondsWith[List[User]](OK, description = "Return all users")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.length should beEqualTo(5)
        },
      onRequest(queryParameters = (Some(1), Some(2), None))
        .respondsWith[List[User]](OK, description = "Return first page with 2 users")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.length should beEqualTo(2)
        },
      onRequest(queryParameters = (None, None, Some("jane")))
        .respondsWith[List[User]](OK, description = "Return users matching 'jane'")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.length should beEqualTo(1)
        }
    )
  )

}
```

### Getting user by id

```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.specs2.specification.core.AsExecution
import pl.iterators.example.baklava.UserApiServer.*

class GetUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      description = "Get a specific user by ID",
      summary = "Retrieve a specific user",
      tags = List("Users")
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

### Create new user

```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.specs2.specification.core.AsExecution
import pl.iterators.example.baklava.UserApiServer.*

class PostUsersRouteSpec extends BaseRouteSpec {

  path(path = "/users")(
    supports(
      POST,
      description = "Create a new user",
      summary = "Create a new user",
      tags = List("Users")
    )(
      onRequest(body = CreateUserRequest("Test User", "test@example.com"))
        .respondsWith[User](Created, description = "User created successfully")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body should beEqualTo {
            User(6L, "Test User", "test@example.com")
          }
          response.headers.exists(h => h.name == "Location" && h.value == s"/users/${response.body.id}") should beTrue
        }
    )
  )

}
```

### Update user

```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.specs2.specification.core.AsExecution
import pl.iterators.example.baklava.UserApiServer.*

class PutUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      PUT,
      pathParameters = p[Long]("userId"),
      description = "Update an existing user",
      summary = "Update an existing user",
      tags = List("Users")
    )(
      onRequest(pathParameters = (1L), body = UpdateUserRequest(Some("Updated User"), Some("updated@example.com")))
        .respondsWith[User](OK, description = "User updated successfully")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body should beEqualTo {
            User(1L, "Updated User", "updated@example.com")
          }
        },
      onRequest(pathParameters = (999L), body = UpdateUserRequest(Some("Updated User"), Some("updated@example.com")))
        .respondsWith[ErrorResponse](NotFound, description = "Return 404 for non-existent user")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body should beEqualTo {
            ErrorResponse("User does not exist", "USER_NOT_FOUND")
          }
        }
    )
  )

}
```

### Delete user

```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.specs2.specification.core.AsExecution
import pl.iterators.baklava.EmptyBody
import pl.iterators.example.baklava.UserApiServer.*

class DeleteUsersUserIdRouteSpec extends BaseRouteSpec {

  path(path = "/users/{userId}")(
    supports(
      DELETE,
      pathParameters = p[Long]("userId"),
      description = "Delete an existing user",
      summary = "Delete an existing user",
      tags = List("Users")
    )(
      onRequest(pathParameters = (1L))
        .respondsWith[EmptyBody](NoContent, description = "User deleted successfully")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.status.status should beEqualTo(NoContent.status)
        },
      onRequest(pathParameters = (999L))
        .respondsWith[ErrorResponse](NotFound, description = "Return 404 for non-existent user")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body should beEqualTo {
            ErrorResponse("User does not exist", "USER_NOT_FOUND")
          }
        }
    )
  )

}
```

## Authorization

Define security schema in test case:
```scala
import pl.iterators.baklava.{HttpBearer, SecurityScheme}

  val bearer: HttpBearer           = HttpBearer("Bearer ", "")
  val bearerScheme: SecurityScheme = SecurityScheme("bearer", bearer)
```

Then add `security` attribute in `onRequest` and test for getting all users with authorization can look like that:
```scala
package pl.iterators.example.baklava

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.specs2.specification.core.AsExecution
import pl.iterators.baklava.{HttpBearer, SecurityScheme}
import pl.iterators.example.baklava.UserApiServer.*

class GetUsersRouteSpec extends BaseRouteSpec {

  val bearer: HttpBearer           = HttpBearer("Bearer ", "")
  val bearerScheme: SecurityScheme = SecurityScheme("bearer", bearer)

  path(path = "/users")(
    supports(
      GET,
      description = "Get all users with optional pagination and search",
      summary = "Retrieve a list of users",
      securitySchemes = Seq(bearerScheme),
      queryParameters = (
        q[Option[Int]]("page"),
        q[Option[Int]]("limit"),
        q[Option[String]]("search")
      ),
      tags = List("Users")
    )(
      onRequest(queryParameters = (None, None, None))
        .respondsWith[ErrorResponse](Unauthorized, description = "Return 401 for missing token")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body should beEqualTo {
            ErrorResponse("Unauthorized", "UNAUTHORIZED")
          }
        },
      onRequest(queryParameters = (None, None, None), security = bearer.apply("token"))
        .respondsWith[List[User]](OK, description = "Return all users")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.length should beEqualTo(5)
        },
      onRequest(queryParameters = (Some(1), Some(2), None), security = bearer.apply("token"))
        .respondsWith[List[User]](OK, description = "Return first page with 2 users")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.length should beEqualTo(2)
        },
      onRequest(queryParameters = (None, None, Some("jane")), security = bearer.apply("token"))
        .respondsWith[List[User]](OK, description = "Return users matching 'jane'")
        .assert { ctx =>
          val response = ctx.performRequest(allRoutes)

          response.body.length should beEqualTo(1)
        }
    )
  )

}
```
