package pl.iterators.baklava.openapi

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.syntax.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.{EmptyBody, HttpBearer, SecurityScheme}
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import scala.concurrent.ExecutionContext

class SimpleFormatterDemoSpec
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

  val routes: Route = complete(org.apache.pekko.http.scaladsl.model.StatusCodes.OK)

  def strictHeaderCheckDefault: Boolean = false

  case class User(id: Long, name: String, email: String)
  case class CreateUserRequest(name: String, email: String)
  case class ErrorResponse(message: String, code: String)
  case class PaginatedUsers(users: Seq[User], total: Int, page: Int)

  private def jsonResponse(statusCode: org.apache.pekko.http.scaladsl.model.StatusCode, json: String): HttpResponse =
    HttpResponse(statusCode, entity = HttpEntity(ContentTypes.`application/json`, json))

  private def emptyResponse(statusCode: org.apache.pekko.http.scaladsl.model.StatusCode): HttpResponse =
    HttpResponse(statusCode)

  val bearer: HttpBearer           = HttpBearer(bearerFormat = "JWT")
  val bearerScheme: SecurityScheme = SecurityScheme("bearerAuth", bearer)

  private var nextResponse: HttpResponse = HttpResponse(OK)

  def performRequest(routes: Route, request: HttpRequest): HttpResponse = nextResponse

  path("/users", description = "User management endpoints", summary = "Manage users")(
    supports(
      GET,
      queryParameters = (q[Option[Int]]("page", "Page number"), q[Option[Int]]("limit", "Items per page")),
      headers = h[String]("Accept"),
      description = "List all users with pagination",
      summary = "List users",
      operationId = "listUsers",
      tags = Seq("Users")
    )(
      onRequest(queryParameters = (Some(1), Some(10)), headers = "application/json")
        .respondsWith[PaginatedUsers](OK, description = "Users listed successfully")
        .assert { ctx =>
          nextResponse = jsonResponse(
            OK,
            PaginatedUsers(Seq(User(1, "Alice", "alice@example.com"), User(2, "Bob", "bob@example.com")), 2, 1).asJson.noSpaces
          )
          ctx.performRequest(routes)
        }
    ),
    supports(
      POST,
      securitySchemes = Seq(bearerScheme),
      description = "Create a new user",
      summary = "Create user",
      operationId = "createUser",
      tags = Seq("Users")
    )(
      onRequest(body = CreateUserRequest("Alice", "alice@example.com"), security = bearer("test-token"))
        .respondsWith[User](Created, description = "User created successfully")
        .assert { ctx =>
          nextResponse = jsonResponse(Created, User(3, "Alice", "alice@example.com").asJson.noSpaces)
          ctx.performRequest(routes)
        },
      onRequest(body = CreateUserRequest("", "invalid"), security = bearer("test-token"))
        .respondsWith[ErrorResponse](BadRequest, description = "Validation error")
        .assert { ctx =>
          nextResponse = jsonResponse(BadRequest, ErrorResponse("Name is required", "VALIDATION_ERROR").asJson.noSpaces)
          ctx.performRequest(routes)
        }
    )
  )

  path("/users/{userId}", description = "Single user operations", summary = "User by ID")(
    supports(
      GET,
      pathParameters = p[Long]("userId", "The user ID"),
      securitySchemes = Seq(bearerScheme),
      description = "Get a user by their unique identifier",
      summary = "Get user",
      operationId = "getUser",
      tags = Seq("Users")
    )(
      onRequest(pathParameters = 1L, security = bearer("test-token"))
        .respondsWith[User](OK, description = "User found")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, User(1, "Alice", "alice@example.com").asJson.noSpaces)
          ctx.performRequest(routes)
        }
    ),
    supports(
      DELETE,
      pathParameters = p[Long]("userId", "The user ID"),
      securitySchemes = Seq(bearerScheme),
      description = "Delete a user",
      summary = "Delete user",
      operationId = "deleteUser",
      tags = Seq("Users")
    )(
      onRequest(pathParameters = 1L, security = bearer("test-token"))
        .respondsWith[EmptyBody](NoContent, description = "User deleted")
        .assert { ctx =>
          nextResponse = emptyResponse(NoContent)
          ctx.performRequest(routes)
        }
    )
  )

  path("/health", summary = "Health check")(
    supports(
      GET,
      description = "Check if the service is healthy",
      summary = "Health check",
      operationId = "healthCheck",
      tags = Seq("System")
    )(
      onRequest()
        .respondsWith[EmptyBody](OK, description = "Service is healthy")
        .assert { ctx =>
          nextResponse = emptyResponse(OK)
          ctx.performRequest(routes)
        }
    )
  )
}
