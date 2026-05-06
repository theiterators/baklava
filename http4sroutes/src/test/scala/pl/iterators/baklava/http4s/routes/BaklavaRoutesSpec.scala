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

  describe("BaklavaRoutesConfig defaults") {
    it("matches the documented defaults") {
      val cfg = BaklavaRoutesConfig()
      cfg.enabled shouldBe true
      cfg.basicAuthUser shouldBe None
      cfg.basicAuthPassword shouldBe None
      cfg.fileSystemPath shouldBe "./target/baklava"
      cfg.publicPathPrefix shouldBe "/"
      cfg.apiPublicPathPrefix shouldBe "/v1"
    }
  }

  describe("BaklavaRoutesConfig.fromEnv(env)") {
    it("returns the documented defaults when the env map is empty") {
      BaklavaRoutesConfig.fromEnv(Map.empty) shouldBe BaklavaRoutesConfig()
    }

    it("maps every BAKLAVA_ROUTES_* variable onto the matching field") {
      val cfg = BaklavaRoutesConfig.fromEnv(
        Map(
          "BAKLAVA_ROUTES_ENABLED"                -> "false",
          "BAKLAVA_ROUTES_BASIC_AUTH_USER"        -> "admin",
          "BAKLAVA_ROUTES_BASIC_AUTH_PASSWORD"    -> "secret",
          "BAKLAVA_ROUTES_FILESYSTEM_PATH"        -> "/srv/docs",
          "BAKLAVA_ROUTES_PUBLIC_PATH_PREFIX"     -> "/x/",
          "BAKLAVA_ROUTES_API_PUBLIC_PATH_PREFIX" -> "/api/v9"
        )
      )
      cfg shouldBe BaklavaRoutesConfig(
        enabled = false,
        basicAuthUser = Some("admin"),
        basicAuthPassword = Some("secret"),
        fileSystemPath = "/srv/docs",
        publicPathPrefix = "/x/",
        apiPublicPathPrefix = "/api/v9"
      )
    }

    it("falls back to a field's default when only some variables are set") {
      val cfg = BaklavaRoutesConfig.fromEnv(Map("BAKLAVA_ROUTES_PUBLIC_PATH_PREFIX" -> "/docs"))
      cfg.publicPathPrefix shouldBe "/docs"
      cfg.fileSystemPath shouldBe BaklavaRoutesConfig().fileSystemPath
      cfg.enabled shouldBe true
    }
  }
}
