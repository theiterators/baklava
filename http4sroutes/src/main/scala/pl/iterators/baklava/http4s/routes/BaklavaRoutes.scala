package pl.iterators.baklava.http4s.routes

import cats.data.Kleisli
import cats.effect.IO
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.{Location, `Content-Type`}
import org.http4s.server.middleware.authentication.BasicAuth
import org.webjars.WebJarAssetLocator

import scala.io.Source
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Try, Using}

object BaklavaRoutes {

  /** The swagger-ui webjar version is the source of truth; we serve assets at `/swagger-ui/<version>/...` so the version must match the
    * resources actually on the classpath. Read the webjar's own metadata rather than hard-coding it here — otherwise a webjar upgrade in
    * build.sbt silently breaks these routes with 404s.
    *
    * If the webjar is missing from the classpath (the user didn't add the dependency) we fail fast — letting the route respond with a
    * confusing 404 at request time would be worse than a clear startup error.
    */
  private lazy val swaggerVersion: String =
    Option(new WebJarAssetLocator().getWebJars.get("swagger-ui")).getOrElse(
      throw new IllegalStateException(
        "swagger-ui webjar not on the classpath — add `\"org.webjars\" % \"swagger-ui\" % \"...\"` to your project's dependencies " +
          "or remove baklava-http4s-routes if you don't intend to serve SwaggerUI."
      )
    )

  private val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger("pl.iterators.baklava.http4s.routes.BaklavaRoutes")

  /** Ensure a public-path prefix ends with exactly one trailing slash. The configured prefix is concatenated with fixed sub-paths
    * (`"swagger-ui/<v>/index.html"`, `"openapi"`); missing the separator silently produced `/api-docsswagger-ui/...` for
    * `public-path-prefix = "/api-docs"`.
    */
  private def withTrailingSlash(prefix: String): String =
    if (prefix.endsWith("/")) prefix else prefix + "/"

  def routes(config: com.typesafe.config.Config): HttpRoutes[IO] = {
    val internalConfig = Config(config)
    if (!internalConfig.enabled) HttpRoutes.empty[IO]
    else
      (internalConfig.basicAuthUser, internalConfig.basicAuthPassword) match {
        case (Some(user), Some(password)) =>
          // `BasicAuth` expects `AuthedRoutes`; adapt the plain routes by ignoring the auth info.
          val validate: BasicAuth.BasicAuthenticator[IO, Unit] = creds =>
            IO.pure(if (creds.username == user && creds.password == password) Some(()) else None)
          val authed: AuthedRoutes[Unit, IO] = Kleisli(ar => coreRoutes(internalConfig).run(ar.req))
          val middleware                     = BasicAuth[IO, Unit]("docs", validate)
          middleware(authed)
        case _ => coreRoutes(internalConfig)
      }
  }

  private def coreRoutes(c: Config): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "openapi" =>
      // `openApiFileContent` does blocking file I/O + YAML parsing; wrap in IO.blocking so
      // exceptions land inside the effect (a missing or malformed file becomes a 404/500, not a
      // crashed request handler). Error bodies are generic; the real cause goes to the log only.
      IO.blocking(openApiFileContent(c))
        .flatMap(content => Ok(content).map(_.withContentType(`Content-Type`(MediaType.text.yaml))))
        .recoverWith {
          case _: java.io.FileNotFoundException =>
            NotFound("openapi document not available — run `sbt test` first to generate it")
          case e: Throwable =>
            IO(log.warn(s"Failed to serve openapi document from ${c.fileSystemPath}/openapi/openapi.yml", e)) *>
            InternalServerError("Failed to serve openapi document")
        }

    case GET -> Root / "swagger-ui" / version / "swagger-initializer.js" if version == swaggerVersion =>
      Ok(swaggerInitializerContent(c)).map(_.withContentType(`Content-Type`(MediaType.application.javascript)))

    case req @ GET -> "swagger-ui" /: rest =>
      // Use http4s's built-in `StaticFile.fromResource` — it handles classpath lookup on a
      // blocking dispatcher, streams the body, sets a content-type from the extension, and
      // turns a missing resource into `OptionT.none` (translated to 404 here).
      val relPath = rest.segments.map(_.decoded()).mkString("/")
      Try((new WebJarAssetLocator).getFullPath("swagger-ui", relPath)).toOption match {
        case Some(fullPath) => StaticFile.fromResource(fullPath, Some(req)).getOrElseF(NotFound())
        case None           => NotFound()
      }

    case GET -> Root / "swagger" =>
      val swaggerUiUrl = s"${withTrailingSlash(c.publicPathPrefix)}swagger-ui/$swaggerVersion/index.html"
      Uri.fromString(swaggerUiUrl) match {
        case Right(uri) => SeeOther(Location(uri))
        case Left(_)    =>
          IO(log.warn(s"Invalid swagger-ui URL constructed from publicPathPrefix='${c.publicPathPrefix}': $swaggerUiUrl")) *>
          InternalServerError("Misconfigured swagger-ui URL")
      }
  }

  // Swagger UI's yaml includes a `servers:` block that Swagger UI uses as the API base URL. We
  // parse, replace with the user-configured `api-public-path-prefix`, then re-serialize so the
  // rendered docs can call the real API directly.
  private def openApiFileContent(c: Config): String =
    Using.resource(Source.fromFile(s"${c.fileSystemPath}/openapi/openapi.yml")) { source =>
      val parser  = new OpenAPIV3Parser
      val openApi = parser.readContents(source.mkString, null, null).getOpenAPI
      val server  = new Server()
      server.setUrl(c.apiPublicPathPrefix)
      openApi.setServers(List(server).asJava)
      Yaml.pretty(openApi)
    }

  private def swaggerInitializerContent(c: Config): String = {
    val swaggerDocsUrl = s"${withTrailingSlash(c.publicPathPrefix)}openapi"
    s"""
       |window.onload = function() {
       |  window.ui = SwaggerUIBundle({
       |    url: "$swaggerDocsUrl",
       |    dom_id: '#swagger-ui',
       |    deepLinking: true,
       |    presets: [
       |      SwaggerUIBundle.presets.apis,
       |      SwaggerUIStandalonePreset
       |    ],
       |    plugins: [
       |      SwaggerUIBundle.plugins.DownloadUrl
       |    ],
       |    layout: "BaseLayout"
       |  });
       |};
       |""".stripMargin
  }

  private case class Config(
      enabled: Boolean,
      basicAuthUser: Option[String],
      basicAuthPassword: Option[String],
      fileSystemPath: String,
      publicPathPrefix: String,
      apiPublicPathPrefix: String
  )

  private object Config {
    def apply(config: com.typesafe.config.Config): Config = {
      val c = config.getConfig("baklava-routes")
      Config(
        enabled = c.getBoolean("enabled"),
        basicAuthUser = Try(c.getString("basic-auth-user")).toOption,
        basicAuthPassword = Try(c.getString("basic-auth-password")).toOption,
        fileSystemPath = c.getString("filesystem-path"),
        publicPathPrefix = c.getString("public-path-prefix"),
        apiPublicPathPrefix = c.getString("api-public-path-prefix")
      )
    }
  }

}
