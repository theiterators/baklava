package pl.iterators.baklava.openapi

import org.http4s.Method.*
import org.http4s.Status.*
import org.http4s.UrlForm
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import pl.iterators.baklava.FormOf

case class User(
    id: Int,
    username: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String],
    password: Option[String],
    phone: Option[String],
    userStatus: Int
)

class PetStoreUserSpec extends PetStoreHttp4sItSpec {
  private val exampleUser1 = User(
    id = 10,
    username = Some("theUser"),
    firstName = Some("John"),
    lastName = Some("James"),
    email = Some("john@email.com"),
    password = Some("12345"),
    phone = Some("12345"),
    userStatus = 1
  )

  path("/user")(
    supports(
      POST,
      summary = "Create user",
      description = "This can only be done by the logged in user.",
      operationId = "createUser",
      tags = Seq("user")
    )(
      onRequest(body = exampleUser1).respondsWith[User](Ok, description = "successful operation").assert { ctx =>
        ctx.performRequest(routes)
      },
      onRequest(body =
        FormOf[User](
          "id"         -> "11",
          "userStatus" -> "1"
        )
      )
        .respondsWith[User](Ok, description = "successful operation but with form")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(body = UrlForm("complete" -> "garbage"))
        .respondsWith[Error](InternalServerError, description = "Error on missing fields")
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )

  path("/user/login")(
    supports(
      GET,
      summary = "Logs user into the system",
      queryParameters = (q[Option[String]]("username"), q[Option[String]]("password")),
      operationId = "loginUser",
      tags = Seq("user")
    )(
      onRequest(queryParameters = (Option("username"), Option("password")))
        .respondsWith[String](Ok, description = "successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )

  path("/user/logout")(
    supports(
      GET,
      summary = "Logs out current logged in user session",
      operationId = "logoutUser",
      tags = Seq("user")
    )(
      onRequest().respondsWith[String](Ok, description = "successful operation").assert { ctx =>
        ctx.performRequest(routes)
      }
    )
  )

  path("/user/{username}")(
    supports(
      GET,
      pathParameters = p[String]("username"),
      summary = "Get user by user name",
      description = "Get user by user name",
      operationId = "getUserByName",
      tags = Seq("user")
    )(
      onRequest(pathParameters = "theUser").respondsWith[User](Ok, description = "successful operation").assert { ctx =>
        ctx.performRequest(routes)
      },
      onRequest(pathParameters = "unknownUser")
        .respondsWith[Error](InternalServerError, description = "User not found") // this fails, it should be 404
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )
}
