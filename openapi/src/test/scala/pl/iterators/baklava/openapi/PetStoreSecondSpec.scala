package pl.iterators.baklava.openapi

import org.http4s.Method.*
import org.http4s.Status.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*

case class User(
    id: Option[Int],
    username: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    email: Option[String],
    password: Option[String],
    phone: Option[String],
    userStatus: Option[Int]
)

class PetStoreSecondSpec extends PetStoreHttp4sItSpec {
  val exampleUser = User(
    id = Some(10),
    username = Some("theUser"),
    firstName = Some("John"),
    lastName = Some("James"),
    email = Some("john@email.com"),
    password = Some("12345"),
    phone = Some("12345"),
    userStatus = Some(1)
  )

  path("/user")(
    supports(
      POST,
      summary = "Create user",
      description = "This can only be done by the logged in user.",
      operationId = "createUser",
      tags = Seq("user")
    )(
      onRequest(body = exampleUser).respondsWith[User](Ok, description = "successful operation").assert { ctx =>
        ctx.performRequest(routes)
        ok
      }
    )
  )
}
