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
import pl.iterators.baklava.pekkohttp.BaklavaPekkoHttp
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.{HttpBearer, SecurityScheme}
import pl.iterators.kebs.circe.KebsCirce
import pl.iterators.kebs.circe.enums.KebsCirceEnumsLowercase
import pl.iterators.kebs.enumeratum.KebsEnumeratum

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext

class BackwardCompatibilitySpec
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

  case class Widget(id: Long, label: String)
  case class CreateWidget(label: String)

  val bearer: HttpBearer           = HttpBearer(bearerFormat = "JWT")
  val bearerScheme: SecurityScheme = SecurityScheme("bearerAuth", bearer)

  private val lastRequestPath: AtomicReference[String]         = new AtomicReference("")
  private val lastAuthorizationHeader: AtomicReference[String] = new AtomicReference("")
  private var nextResponse: HttpResponse                       = HttpResponse(OK)

  def performRequest(routes: Route, request: HttpRequest): HttpResponse = {
    lastRequestPath.set(request.uri.path.toString + request.uri.rawQueryString.map("?" + _).getOrElse(""))
    request.headers.find(_.name.toLowerCase == "authorization").foreach(h => lastAuthorizationHeader.set(h.value))
    nextResponse
  }

  private def jsonResponse(statusCode: org.apache.pekko.http.scaladsl.model.StatusCode, json: String): HttpResponse =
    HttpResponse(statusCode, entity = HttpEntity(ContentTypes.`application/json`, json))

  path("/widgets/{widgetId}")(
    supports(
      GET,
      pathParameters = p[Long]("widgetId"),
      queryParameters = q[Option[String]]("filter", "Optional filter"),
      securitySchemes = Seq(bearerScheme),
      tags = Seq("Widgets")
    )(
      onRequest(pathParameters = 5L, queryParameters = Some("shiny"), security = bearer("tok123"))
        .respondsWith[Widget](OK, description = "Eager path + query + security still work")
        .assert { ctx =>
          nextResponse = jsonResponse(OK, Widget(5L, "Alice").asJson.noSpaces)
          val response = ctx.performRequest(routes)
          response.body.id shouldBe 5L
          lastRequestPath.get() shouldBe "/widgets/5?filter=shiny"
          lastAuthorizationHeader.get() shouldBe "Bearer tok123"
        }
    )
  )

  path("/widgets")(
    supports(
      POST,
      securitySchemes = Seq(bearerScheme),
      tags = Seq("Widgets")
    )(
      onRequest(body = CreateWidget("gizmo"), security = bearer("tok456"))
        .respondsWith[Widget](Created, description = "Eager body + security still work")
        .assert { ctx =>
          nextResponse = jsonResponse(Created, Widget(11L, "gizmo").asJson.noSpaces)
          val response = ctx.performRequest(routes)
          response.body.label shouldBe "gizmo"
          lastAuthorizationHeader.get() shouldBe "Bearer tok456"
        }
    )
  )
}
