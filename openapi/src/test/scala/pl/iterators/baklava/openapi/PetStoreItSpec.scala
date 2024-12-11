package pl.iterators.baklava.openapi

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshalling.ToEntityMarshaller
import org.apache.pekko.http.scaladsl.model.{HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.complete
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import org.specs2.specification.core.{AsExecution, Fragment, Fragments}
import pl.iterators.baklava.BaklavaGlobal
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.specs2.BaklavaSpecs2
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext}

trait PetStoreItSpec
    extends SpecificationLike
    with AfterAll
    with BaklavaPekkoHttp[Fragment, Fragments, AsExecution]
    with BaklavaSpecs2[Route, ToEntityMarshaller, FromEntityUnmarshaller]
    with FailFastCirceSupport
    with KebsCirce
    with KebsCirceEnumsLowercase
    with KebsEnumeratum {

  private implicit val system: ActorSystem        = ActorSystem()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val materializer: Materializer         = Materializer(system)

  val routes: Route = complete(StatusCodes.OK)

  override def performRequest(routes: Route, request: HttpRequest): HttpResponse = {
    val fixedRequest = request.withUri("https://petstore.swagger.io/v2" + request.uri.path.toString())
    println(fixedRequest)

    fixedRequest.entity match {
      case HttpEntity.Strict(_, data) => println(data.utf8String)
      case _                          => println("not strict")
    }

    println(Await.result(Http().singleRequest(fixedRequest), Duration.Inf))
    Await.result(Http().singleRequest(fixedRequest), Duration.Inf)
  }

  override def afterAll(): Unit = {
    val openAPI = new OpenAPI()
      .info(
        new io.swagger.v3.oas.models.info.Info()
          .title("Swagger Petstore")
          .version("1.0.7")
          .description(
            "This is a sample server Petstore server.  You can find out more about Swagger at [http://swagger.io](http://swagger.io) or on [irc.freenode.net, #swagger](http://swagger.io/irc/).  For this sample, you can use the api key `special-key` to test the authorization filters."
          )
          .termsOfService("http://swagger.io/terms/")
          .contact(new io.swagger.v3.oas.models.info.Contact().email("apiteam@swagger.io"))
          .license(
            new io.swagger.v3.oas.models.info.License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0.html")
          )
      )
      .addServersItem(new io.swagger.v3.oas.models.servers.Server().url("https://petstore.swagger.io/v2"))
      .addTagsItem(new io.swagger.v3.oas.models.tags.Tag().name("pet").description("Everything about your Pets"))
      .addTagsItem(new io.swagger.v3.oas.models.tags.Tag().name("store").description("Access to Petstore orders"))
      .addTagsItem(new io.swagger.v3.oas.models.tags.Tag().name("user").description("Operations about user"))

    println(Json.pretty(OpenAPIGenerator.from(openAPI, BaklavaGlobal.get)))
    BaklavaGlobal.print()
  }
}
