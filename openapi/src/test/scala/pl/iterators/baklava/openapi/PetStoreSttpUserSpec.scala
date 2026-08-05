package pl.iterators.baklava.openapi

import io.circe.{Decoder, Encoder}
import pl.iterators.baklava.FormOf
import sttp.model.{Method, StatusCode}

case class SttpUser(
    id: Int,
    username: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String],
    password: Option[String],
    phone: Option[String],
    userStatus: Int
)

object SttpUser {
  implicit val encoder: Encoder[SttpUser] =
    Encoder.forProduct8("id", "username", "firstName", "lastName", "email", "password", "phone", "userStatus")(u =>
      (u.id, u.username, u.firstName, u.lastName, u.email, u.password, u.phone, u.userStatus)
    )
  implicit val decoder: Decoder[SttpUser] =
    Decoder.forProduct8("id", "username", "firstName", "lastName", "email", "password", "phone", "userStatus")(SttpUser.apply)
}

class PetStoreSttpUserSpec extends PetStoreSttpItSpec {
  private val exampleUser = SttpUser(
    id = 20,
    username = Some("sttpUser"),
    firstName = Some("John"),
    lastName = Some("James"),
    email = Some("john@email.com"),
    password = Some("12345"),
    phone = Some("12345"),
    userStatus = 1
  )

  path("/user")(
    supports(
      Method.POST,
      summary = "Create user",
      description = "This can only be done by the logged in user.",
      operationId = "createUser",
      tags = Seq("user")
    )(
      onRequest(body = exampleUser).respondsWith[SttpUser](StatusCode.Ok, description = "successful operation").assert { ctx =>
        ctx.performRequest(defaultBackend)
      },
      onRequest(body =
        FormOf[SttpUser](
          "id"         -> "21",
          "userStatus" -> "1"
        )
      )
        .respondsWith[SttpUser](StatusCode.Ok, description = "successful operation but with form")
        .assert { ctx =>
          ctx.performRequest(defaultBackend)
        }
    )
  )

  path("/user/login")(
    supports(
      Method.GET,
      summary = "Logs user into the system",
      queryParameters = (q[Option[String]]("username"), q[Option[String]]("password")),
      operationId = "loginUser",
      tags = Seq("user")
    )(
      onRequest(queryParameters = (Option("username"), Option("password")))
        .respondsWith[String](StatusCode.Ok, description = "successful operation")
        .assert { ctx =>
          ctx.performRequest(defaultBackend)
        }
    )
  )
}
