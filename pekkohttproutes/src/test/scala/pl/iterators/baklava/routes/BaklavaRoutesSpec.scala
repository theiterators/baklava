package pl.iterators.baklava.routes

import com.typesafe.config.ConfigFactory
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, Location}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.webjars.WebJarAssetLocator

class BaklavaRoutesSpec extends AnyFunSpec with Matchers with ScalatestRouteTest {

  private val swaggerVersion: String =
    Option(new WebJarAssetLocator().getWebJars.get("swagger-ui")).getOrElse(
      throw new IllegalStateException("swagger-ui webjar not on the classpath in tests")
    )

  describe("BaklavaRoutes.routes(BaklavaRoutesConfig)") {

    it("rejects every path when disabled") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(enabled = false))
      Get("/swagger") ~> r ~> check {
        handled shouldBe false
      }
    }

    it("redirects /swagger to the swagger-ui index, honoring publicPathPrefix without trailing slash") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/internal/docs"))
      Get("/swagger") ~> r ~> check {
        status shouldBe StatusCodes.SeeOther
        header[Location].map(_.uri.toString).get shouldBe s"/internal/docs/swagger-ui/$swaggerVersion/index.html"
      }
    }

    it("redirects /swagger correctly when publicPathPrefix already has trailing slash") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/internal/docs/"))
      Get("/swagger") ~> r ~> check {
        header[Location].map(_.uri.toString).get shouldBe s"/internal/docs/swagger-ui/$swaggerVersion/index.html"
      }
    }

    it("returns the swagger initializer JS pointing at the configured openapi URL (no trailing slash)") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/api-docs"))
      Get(s"/swagger-ui/$swaggerVersion/swagger-initializer.js") ~> r ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] should include("\"/api-docs/openapi\"")
      }
    }

    it("returns the swagger initializer JS correctly when publicPathPrefix has trailing slash") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(publicPathPrefix = "/api-docs/"))
      Get(s"/swagger-ui/$swaggerVersion/swagger-initializer.js") ~> r ~> check {
        responseAs[String] should include("\"/api-docs/openapi\"")
      }
    }

    it("returns a helpful 404 when the openapi file is missing") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(fileSystemPath = "./target/baklava-routes-test-nonexistent"))
      Get("/openapi") ~> r ~> check {
        status shouldBe StatusCodes.NotFound
        responseAs[String] should include("run `sbt test` first")
      }
    }

    it("challenges with 401 when basic auth is configured but no credentials are sent") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(basicAuthUser = Some("u"), basicAuthPassword = Some("p")))
      Get("/swagger") ~> Route.seal(r) ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    it("rejects wrong basic-auth credentials") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(basicAuthUser = Some("u"), basicAuthPassword = Some("p")))
      Get("/swagger") ~> addCredentials(BasicHttpCredentials("u", "wrong")) ~> Route.seal(r) ~> check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    it("accepts correct basic-auth credentials and serves the redirect") {
      val r = BaklavaRoutes.routes(BaklavaRoutesConfig(basicAuthUser = Some("u"), basicAuthPassword = Some("p")))
      Get("/swagger") ~> addCredentials(BasicHttpCredentials("u", "p")) ~> r ~> check {
        status shouldBe StatusCodes.SeeOther
      }
    }
  }

  describe("BaklavaRoutes.routes(com.typesafe.config.Config)") {

    it("delegates to the case-class form via fromTypesafeConfig") {
      val cfg = ConfigFactory.parseString(
        """
          |baklava-routes {
          |  enabled = true
          |  filesystem-path = "./target/baklava"
          |  public-path-prefix = "/from-hocon/"
          |  api-public-path-prefix = "/v1"
          |}
          |""".stripMargin
      )
      val r = BaklavaRoutes.routes(cfg)
      Get("/swagger") ~> r ~> check {
        status shouldBe StatusCodes.SeeOther
        header[Location].map(_.uri.toString).get shouldBe s"/from-hocon/swagger-ui/$swaggerVersion/index.html"
      }
    }
  }

  describe("BaklavaRoutesConfig.fromTypesafeConfig") {

    it("parses every field, leaving optional creds as None when absent") {
      val cfg = ConfigFactory.parseString(
        """
          |baklava-routes {
          |  enabled = false
          |  filesystem-path = "/tmp/foo"
          |  public-path-prefix = "/x/"
          |  api-public-path-prefix = "/api/v9"
          |}
          |""".stripMargin
      )
      val parsed = BaklavaRoutesConfig.fromTypesafeConfig(cfg)
      parsed.enabled shouldBe false
      parsed.fileSystemPath shouldBe "/tmp/foo"
      parsed.publicPathPrefix shouldBe "/x/"
      parsed.apiPublicPathPrefix shouldBe "/api/v9"
      parsed.basicAuthUser shouldBe None
      parsed.basicAuthPassword shouldBe None
    }

    it("parses basic-auth credentials when present") {
      val cfg = ConfigFactory.parseString(
        """
          |baklava-routes {
          |  enabled = true
          |  basic-auth-user = "admin"
          |  basic-auth-password = "secret"
          |  filesystem-path = "."
          |  public-path-prefix = "/"
          |  api-public-path-prefix = "/v1"
          |}
          |""".stripMargin
      )
      val parsed = BaklavaRoutesConfig.fromTypesafeConfig(cfg)
      parsed.basicAuthUser shouldBe Some("admin")
      parsed.basicAuthPassword shouldBe Some("secret")
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
