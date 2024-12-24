package pl.iterators.baklava.openapi

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import pl.iterators.baklava.{Schema, SchemaType}

sealed trait Status extends EnumEntry with Lowercase
object Status extends Enum[Status] {
  case object Available extends Status
  case object Pending   extends Status
  case object Sold      extends Status

  def values: IndexedSeq[Status] = findValues

  implicit val schema: Schema[Status] = new Schema[Status] {
    val `type`: SchemaType                 = SchemaType.StringType
    val className: String                  = "Status"
    val format: Option[String]             = None
    val properties: Map[String, Schema[?]] = Map.empty
    val `enum`: Option[Set[String]]        = Some(Status.values.map(_.entryName.toLowerCase).toSet)
    val items: Option[Schema[_]]           = None
    val required: Boolean                  = true
    val additionalProperties: Boolean      = false
    val default: Option[Status]            = None
    val description: Option[String]        = Some("Pet status in the store")
  }
}

case class Tag(id: Option[Long], name: Option[String])
case class Category(id: Option[Long], name: Option[String])
case class Pet(id: Option[Long], name: String, photoUrls: Seq[String], tags: Option[Seq[Tag]], status: Option[Status])

case class Error(code: Int, message: String)

class PetStoreSpec extends PetStorePekkoItSpec {
  private val examplePet = Pet(
    id = Some(1),
    name = "doggie",
    photoUrls = Seq("url1", "url2"),
    tags = Some(Seq(Tag(id = Some(1), name = Some("tag1")))),
    status = Some(Status.Available)
  )

  private val nonExistentPet = Pet(
    id = Some(-1000),
    name = "doggie",
    photoUrls = Seq("string"),
    tags = Some(Seq(Tag(id = Some(0), name = Some("string")))),
    status = Some(Status.Available)
  )

  path("/pet")(
    supports(
      PUT,
      summary = "Update an existing pet",
      description = "Update an existing pet by Id",
      operationId = "updatePet",
      tags = Seq("pet")
    )(
      onRequest(body = examplePet, headers = Map("Accept" -> "application/json"))
        .respondsWith[Pet](OK, description = "Update an existent pet in the store")
        .assert { ctx =>
          ctx.performRequest(routes)
          ok
        },
      onRequest(body = nonExistentPet, headers = Map("Accept" -> "application/json"))
        .respondsWith[String](NotFound, description = "Pet not found")
        .assert { ctx =>
          ctx.performRequest(routes)
          ok
        }
    ),
    supports(
      POST,
      summary = "Add a new pet to the store",
      description = "Add a new pet to the store",
      operationId = "addPet",
      tags = Seq("pet")
    )(
      onRequest(body = examplePet, headers = Map("Accept" -> "application/json"))
        .respondsWith[Pet](OK, description = "Successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
          ok
        },
      onRequest(body = examplePet.copy(name = "doggo", id = Some(2)), headers = Map("Accept" -> "application/json"))
        .respondsWith[Pet](OK, description = "Another successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
          ok
        },
      onRequest.respondsWith[Error](BadRequest, description = "Invalid input").assert { ctx =>
        ctx.performRequest(routes)
        ok
      }
    )
  )
}
