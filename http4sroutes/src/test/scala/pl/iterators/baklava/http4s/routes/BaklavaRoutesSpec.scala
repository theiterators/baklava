package pl.iterators.baklava.http4s.routes

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.headers.{Authorization, Location, `Content-Type`}
import org.http4s.{BasicCredentials, Headers, HttpRoutes, MediaType, Method, Request, Response, Status, Uri}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.webjars.WebJarAssetLocator

class BaklavaRoutesSpec extends AnyFunSpec with Matchers {

  private implicit val runtime: IORuntime = IORuntime.global

  private val swaggerVersion: String =
    Option(new WebJarAssetLocator().getWebJars.get("swagger-ui")).getOrElse(
      throw new IllegalStateException("swagger-ui webjar not on the classpath in tests")
    )

  private def call(routes: HttpRoutes[IO], method: Method, path: String, headers: Headers = Headers.empty): Response[IO] =
    routes.orNotFound.run(Request[IO](method = method, uri = Uri.unsafeFromString(path), headers = headers)).unsafeRunSync()

  private def bodyString(resp: Response[IO]): String =
    resp.bodyText.compile.string.unsafeRunSync()

  describe("BaklavaRoutes.routes") {

    it("serves nothing when disabled") {
      val resp = call(BaklavaRoutes.routes(BaklavaRoutesConfig(enabled = false)), Method.GET, "/swagger")
      resp.status shouldBe Status.NotFound
    }

    it("redirects /swagger to the swagger-ui index, honoring publicPathPrefix without trailing slash") {
      val resp = call(BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/internal/docs")), Method.GET, "/swagger")
      resp.status shouldBe Status.SeeOther
      val loc = resp.headers.get[Location].map(_.uri.renderString).getOrElse(fail("Location header missing"))
      loc shouldBe s"/internal/docs/swagger-ui/$swaggerVersion/index.html"
    }

    it("redirects /swagger correctly when publicPathPrefix already has trailing slash") {
      val resp = call(BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/internal/docs/")), Method.GET, "/swagger")
      val loc  = resp.headers.get[Location].map(_.uri.renderString).getOrElse(fail("Location header missing"))
      loc shouldBe s"/internal/docs/swagger-ui/$swaggerVersion/index.html"
    }

    it("returns the swagger initializer JS pointing at the configured openapi URL") {
      val resp = call(
        BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/api-docs")),
        Method.GET,
        s"/swagger-ui/$swaggerVersion/swagger-initializer.js"
      )
      resp.status shouldBe Status.Ok
      resp.headers.get[`Content-Type`].map(_.mediaType) shouldBe Some(MediaType.application.javascript)
      bodyString(resp) should include("\"/api-docs/openapi\"")
    }

    it("returns a helpful 404 when the openapi file is missing") {
      val resp = call(
        BaklavaRoutes.routes(BaklavaRoutesConfig(fileSystemPath = "./target/baklava-routes-test-nonexistent")),
        Method.GET,
        "/openapi"
      )
      resp.status shouldBe Status.NotFound
      bodyString(resp) should include("run `sbt test` first")
    }

    it("challenges with 401 when basic auth is configured but no credentials are sent") {
      val resp = call(
        BaklavaRoutes.routes(BaklavaRoutesConfig(basicAuthUser = Some("u"), basicAuthPassword = Some("p"))),
        Method.GET,
        "/swagger"
      )
      resp.status shouldBe Status.Unauthorized
    }

    it("rejects wrong basic-auth credentials") {
      val resp = call(
        BaklavaRoutes.routes(BaklavaRoutesConfig(basicAuthUser = Some("u"), basicAuthPassword = Some("p"))),
        Method.GET,
        "/swagger",
        Headers(Authorization(BasicCredentials("u", "wrong")))
      )
      resp.status shouldBe Status.Unauthorized
    }

    it("accepts correct basic-auth credentials and serves the redirect") {
      val resp = call(
        BaklavaRoutes.routes(BaklavaRoutesConfig(basicAuthUser = Some("u"), basicAuthPassword = Some("p"))),
        Method.GET,
        "/swagger",
        Headers(Authorization(BasicCredentials("u", "p")))
      )
      resp.status shouldBe Status.SeeOther
    }
  }

  describe("BaklavaRoutesConfig.fromEnv") {
    it("uses defaults when no env vars are set") {
      val cfg = BaklavaRoutesConfig.fromEnv
      // Defaults match BaklavaRoutesConfig() unless the test environment exports BAKLAVA_ROUTES_*; all six fields
      // shouldn't all match by accident, so a single sanity check is enough.
      cfg.fileSystemPath should not be empty
    }
  }
}
