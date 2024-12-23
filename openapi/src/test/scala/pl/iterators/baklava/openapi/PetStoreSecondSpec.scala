package pl.iterators.baklava.openapi

import org.http4s.Method.*
import org.http4s.Status.*
import pl.iterators.baklava.EmptyBody
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*

class PetStoreSecondSpec extends PetStoreHttp4sItSpec {
  val examplePet = Pet(
    id = Some(1),
    name = "cat",
    photoUrls = Seq("url1", "url2"),
    tags = Some(Seq(Tag(id = Some(1), name = Some("tag1")))),
    status = Some(Status.Available)
  )

  path("/pet")(
    supports(
      POST,
      summary = "Add a new pet to the store",
      description = "Add a new pet to the store",
      operationId = "addPetS",
      tags = Seq("pet")
    )(
      onRequest(body = examplePet).respondsWith[Pet](Ok, description = "Successful operation").assert { ctx =>
        ctx.performRequest(routes)
        ok
      },
      onRequest.respondsWith[Error](UnsupportedMediaType, description = "Invalid input").assert { ctx =>
        ctx.performRequest(routes)
        ok
      }
    )
  )
}
