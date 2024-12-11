package pl.iterators.baklava.openapi

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import io.circe.{Json, JsonNumber, JsonObject}
import org.apache.pekko.http.scaladsl.model.HttpMethods.POST
import org.apache.pekko.http.scaladsl.model.StatusCodes.*

sealed trait Status extends EnumEntry with Lowercase
object Status extends Enum[Status] {
  case object Available extends Status
  case object Pending   extends Status
  case object Sold      extends Status

  val values: IndexedSeq[Status] = findValues
}

case class Tag(id: Option[Long], name: Option[String])
case class Category(id: Option[Long], name: Option[String])
case class Pet(id: Option[Long], name: String, photoUrls: Seq[String], tags: Option[Seq[Tag]], status: Option[Status])

class PetStoreSpec extends PetStoreItSpec {
  val examplePet = Pet(
    id = Some(1),
    name = "doggie",
    photoUrls = Seq("url1", "url2"),
    tags = Some(Seq(Tag(id = Some(1), name = Some("tag1")))),
    status = Some(Status.Available)
  )

  path("/pet")(
    supports(
      POST,
      summary = "Add a new pet to the store",
      description = "Add a new pet to the store",
      operationId = "addPet",
      tags = Seq("pet")
    )(
      onRequest(body = examplePet).respondsWith(OK, description = "Successful operation").assert { ctx =>
        ctx.performRequest(routes)
        ok
      },
      onRequest.respondsWith(UnsupportedMediaType, description = "Invalid input").assert { ctx =>
        ctx.performRequest(routes)
        ok
      }
    )
  )
}
