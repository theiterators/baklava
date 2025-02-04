package pl.iterators.baklava.openapi

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Lowercase
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.model.StatusCodes.*
import pl.iterators.baklava.{
  ApiKeyInHeader,
  OAuth2InBearer,
  OAuthFlows,
  OAuthImplicitFlow,
  Schema,
  SchemaType,
  SecurityScheme,
  ToQueryParam
}

import java.net.URI

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
    val `enum`: Option[Set[String]]        = Some(Status.values.map(_.entryName).toSet)
    val items: Option[Schema[?]]           = None
    val required: Boolean                  = true
    val additionalProperties: Boolean      = false
    val default: Option[Status]            = None
    val description: Option[String]        = Some("Pet status in the store")
  }

  implicit val toQueryParam: ToQueryParam[Status] = new ToQueryParam[Status] {
    def apply(t: Status): Seq[String] = Seq(t.entryName)
  }
}

case class Tag(id: Option[Long], name: Option[String])
case class Category(id: Option[Long], name: Option[String])
case class Pet(id: Option[Long], name: Option[String], photoUrls: Seq[String], tags: Option[Seq[Tag]], status: Option[Status])

case class Error(code: Int, message: String)

class PetStorePetSpec extends PetStorePekkoItSpec {
  val examplePet = Pet(
    id = Some(1),
    name = Some("doggie"),
    photoUrls = Seq("url1", "url2"),
    tags = Some(Seq(Tag(id = Some(1), name = Some("tag1")))),
    status = Some(Status.Available)
  )

  val nonExistentPet = Pet(
    id = Some(-1000),
    name = Some("doggie"),
    photoUrls = Seq("string"),
    tags = Some(Seq(Tag(id = Some(0), name = Some("string")))),
    status = Some(Status.Available)
  )

  val petOauthSecurity: OAuth2InBearer = OAuth2InBearer(
    OAuthFlows(
      implicitFlow = Some(
        OAuthImplicitFlow(
          new URI("https://petstore3.swagger.io/oauth/authorize"),
          scopes = Map(
            "write:pets" -> "modify pets in your account",
            "read:pets"  -> "read your pets"
          )
        )
      )
    )
  )

  val petApiKeySecurity: ApiKeyInHeader = ApiKeyInHeader("api_key")

  val petSecurityOauthScheme: SecurityScheme  = SecurityScheme("petstore_auth", petOauthSecurity)
  val petSecurityApiKeyScheme: SecurityScheme = SecurityScheme("api_key", petApiKeySecurity)

  path("/pet")(
    supports(
      PUT,
      securitySchemes = Seq(petSecurityOauthScheme),
      headers = h[String]("Accept"),
      summary = "Update an existing pet",
      description = "Update an existing pet by Id",
      operationId = "updatePet",
      tags = Seq("pet")
    )(
      onRequest(body = examplePet, security = petOauthSecurity("pet-token"), headers = "application/json")
        .respondsWith[Pet](OK, description = "Update an existent pet in the store")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(body = nonExistentPet, security = petOauthSecurity("pet-token"), headers = "application/json")
        .respondsWith[String](NotFound, description = "Pet not found")
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    ),
    supports(
      POST,
      securitySchemes = Seq(petSecurityOauthScheme),
      headers = h[String]("Accept"),
      summary = "Add a new pet to the store",
      description = "Add a new pet to the store",
      operationId = "addPet",
      tags = Seq("pet")
    )(
      onRequest(body = examplePet, security = petOauthSecurity("pet-token"), headers = "application/json")
        .respondsWith[Pet](OK, description = "Successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(
        body = examplePet.copy(name = Some("doggo"), id = Some(2)),
        security = petOauthSecurity("pet-token"),
        headers = "application/json"
      )
        .respondsWith[Pet](OK, description = "Another successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest.respondsWith[Error](BadRequest, description = "Invalid input").assert { ctx =>
        ctx.performRequest(routes)
      }
    )
  )

  path("/pet/findByStatus")(
    supports(
      GET,
      securitySchemes = Seq(petSecurityOauthScheme),
      queryParameters = q[Status]("status"),
      headers = h[String]("Accept"),
      summary = "Finds Pets by status",
      description = "Multiple status values can be provided with comma separated strings",
      operationId = "findPetsByStatus",
      tags = Seq("pet")
    )(
      onRequest(
        queryParameters = Status.Available,
        security = petOauthSecurity("pet-token"),
        headers = "application/json"
      )
        .respondsWith[Seq[Pet]](OK, description = "Successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(headers = "application/json")
        .respondsWith[String](BadRequest, description = "Invalid status value")
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )

  path("/pet/findByTags")(
    supports(
      GET,
      securitySchemes = Seq(petSecurityOauthScheme),
      queryParameters = q[Seq[String]]("tags"),
      headers = h[String]("Accept"),
      summary = "Finds Pets by tag",
      description = "Multiple tags can be provided with comma separated strings. Use tag1, tag2, tag3 for testing.",
      operationId = "findPetsByTag",
      tags = Seq("pet")
    )(
      onRequest(
        queryParameters = Seq("tag1", "tag2"),
        security = petOauthSecurity("pet-token"),
        headers = "application/json"
      )
        .respondsWith[Seq[Pet]](OK, description = "Successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(headers = "application/json")
        .respondsWith[String](
          BadRequest,
          description = "Invalid tag value"
        )
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )

  path("/pet/{petId}")(
    supports(
      GET,
      securitySchemes = Seq(petSecurityOauthScheme, petSecurityApiKeyScheme),
      pathParameters = p[Int]("petId"),
      headers = h[String]("Accept"),
      summary = "Find pet by ID",
      description = "Returns a single pet",
      operationId = "getPetById",
      tags = Seq("pet")
    )(
      onRequest(pathParameters = 1, security = petOauthSecurity("pet-token"), headers = "application/json")
        .respondsWith[Pet](
          OK,
          description = "Successful operation",
          headers = Seq(
            h[String]("Access-Control-Allow-Headers"),
            h[String]("Access-Control-Allow-Methods"),
            h[String]("Access-Control-Allow-Origin"),
            h[String]("Access-Control-Expose-Headers"),
            h[String]("Date"),
            h[String]("Server")
          ),
          strictHeaderCheck = true
        )
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(pathParameters = 1, security = petApiKeySecurity("my-api-key"), headers = "application/json")
        .respondsWith[Pet](
          OK,
          description = "Successful operation 2",
          headers = Seq(
            h[String]("Access-Control-Allow-Headers"),
            h[String]("Access-Control-Allow-Methods"),
            h[String]("Access-Control-Allow-Origin"),
            h[String]("Access-Control-Expose-Headers"),
            h[String]("Date"),
            h[String]("Server", description = "Server header")
          ),
          strictHeaderCheck = true
        )
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(pathParameters = -2137, security = petOauthSecurity("pet-token"), headers = "application/json")
        .respondsWith[String](NotFound, description = "Pet not found")
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    ),
    supports(
      DELETE,
      securitySchemes = Seq(petSecurityOauthScheme),
      pathParameters = p[Int]("petId"),
      headers = (h[String]("Accept"), h[Option[String]]("api_key")),
      summary = "Deletes a pet",
      operationId = "deletePet",
      tags = Seq("pet")
    )(
      onRequest(pathParameters = 23, headers = ("application/json", Some("my-api-key")))
        .respondsWith[String](OK, description = "Successful operation")
        .assert { ctx =>
          ctx.performRequest(routes)
        },
      onRequest(pathParameters = 23, headers = ("application/json", None))
        .respondsWith[String](OK, description = "Unauthorized") // api_key is ignored :shrug:
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )
}
