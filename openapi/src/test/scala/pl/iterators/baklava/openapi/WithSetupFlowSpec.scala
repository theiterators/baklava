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

class WithSetupFlowSpec
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

  case class Thing(id: Long, name: String)
  case class CreateThing(name: String)

  val bearer: HttpBearer           = HttpBearer(bearerFormat = "JWT")
  val bearerScheme: SecurityScheme = SecurityScheme("bearerAuth", bearer)

  // Captured request path at the mock server — we assert on it.
  private val lastRequestPath: AtomicReference[String] = new AtomicReference("")
  private var nextResponse: HttpResponse               = HttpResponse(OK)

  def performRequest(routes: Route, request: HttpRequest): HttpResponse = {
    lastRequestPath.set(request.uri.path.toString + request.uri.rawQueryString.map("?" + _).getOrElse(""))
    nextResponse
  }

  private def jsonResponse(statusCode: org.apache.pekko.http.scaladsl.model.StatusCode, json: String): HttpResponse =
    HttpResponse(statusCode, entity = HttpEntity(ContentTypes.`application/json`, json))

  path("/things/{thingId}")(
    supports(
      GET,
      pathParameters = p[Long]("thingId", "The thing ID"),
      securitySchemes = Seq(bearerScheme),
      tags = Seq("Things")
    )(
      // Scenario 1: lazy path parameter produced by setup.
      // Since PR #73 removed the overloaded `.request` in favor of a single lazy method, this
      // lambda no longer needs an explicit parameter type annotation on Scala 2.13.
      withSetup {
        42L // pretend this is `seedThing().unsafeRunSync().id`
      }.request { id =>
        onRequest(pathParameters = id, security = bearer("tok"))
      }.respondsWith[Thing](OK, description = "Thing found via setup id")
        .assert { case (ctx, id) =>
          nextResponse = jsonResponse(OK, Thing(id, "Alice").asJson.noSpaces)
          val response = ctx.performRequest(routes)
          response.body.id shouldBe id
          lastRequestPath.get() shouldBe "/things/42"
        }
    )
  )

  path("/things")(
    supports(
      POST,
      securitySchemes = Seq(bearerScheme),
      tags = Seq("Things")
    )(
      // Scenario 2: lazy body produced by setup (tuple) — no lambda annotation needed.
      withSetup {
        ("Bob", 7L) // (name, expectedId)
      }.request { setup =>
        val (name, _) = setup
        onRequest(body = CreateThing(name), security = bearer("tok"))
      }.respondsWith[Thing](Created, description = "Created via setup-derived body")
        .assert { case (ctx, (name, expectedId)) =>
          nextResponse = jsonResponse(Created, Thing(expectedId, name).asJson.noSpaces)
          val response = ctx.performRequest(routes)
          response.body.name shouldBe name
          response.body.id shouldBe expectedId
        }
    )
  )

  path("/things/{thingId}/metadata")(
    supports(
      GET,
      pathParameters = p[Long]("thingId"),
      securitySchemes = Seq(bearerScheme),
      tags = Seq("Things")
    )(
      // Scenario 3: setup yields Unit (side-effect only); eager `.onRequest` is used —
      // distinct method name from the lazy `.request` so Scala 2.13 can infer lambda types
      // cleanly without forcing a single overloaded method.
      withSetup {
        val _ = 1 + 1 // side-effect stand-in
        ()
      }.onRequest(pathParameters = 99L, security = bearer("tok"))
        .respondsWith[Thing](OK, description = "Eager request override with Unit setup")
        .assert { case (ctx, _) =>
          nextResponse = jsonResponse(OK, Thing(99L, "Zed").asJson.noSpaces)
          val response = ctx.performRequest(routes)
          response.body.id shouldBe 99L
          lastRequestPath.get() shouldBe "/things/99/metadata"
        }
    )
  )
}
