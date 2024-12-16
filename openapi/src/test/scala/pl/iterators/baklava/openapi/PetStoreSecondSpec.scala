package pl.iterators.baklava.openapi

import org.apache.pekko.http.scaladsl.model.HttpMethods.POST
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import pl.iterators.baklava.EmptyBody

class PetStoreSecondSpec extends PetStoreItSpec {
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
      onRequest(body = examplePet).respondsWith[EmptyBody](OK, description = "Successful operation").assert { ctx =>
        ctx.performRequest(routes)
        ok
      },
      onRequest.respondsWith[EmptyBody](UnsupportedMediaType, description = "Invalid input").assert { ctx =>
        ctx.performRequest(routes)
        ok
      }
    )
  )
}
