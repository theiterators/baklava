package pl.iterators.baklava.http4s.routes

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.{Authorization, Location, `Content-Type`}
import org.typelevel.ci.CIString
import org.webjars.WebJarAssetLocator

import java.io.{ByteArrayOutputStream, InputStream}
import java.util.Base64
import scala.io.Source
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Failure, Success, Try}

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

  /** slf4j logger used for server-side logging of request-handling failures. The route responses stay generic (no `e.getMessage` in the
    * client body) so internal details — filesystem paths, parser errors — never leak to unauthenticated clients when basic auth is
    * disabled.
    */
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
    else {
      val core = coreRoutes(internalConfig)
      (internalConfig.basicAuthUser, internalConfig.basicAuthPassword) match {
        case (Some(user), Some(password)) => basicAuth(user, password)(core)
        case _                            => core
      }
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

    case GET -> "swagger-ui" /: rest =>
      serveSwaggerAsset(rest.segments.map(_.decoded()).mkString("/"))

    case GET -> Root / "swagger" =>
      val swaggerUiUrl = s"${withTrailingSlash(c.publicPathPrefix)}swagger-ui/$swaggerVersion/index.html"
      Uri.fromString(swaggerUiUrl) match {
        case Right(uri) => SeeOther(Location(uri))
        case Left(_)    =>
          IO(log.warn(s"Invalid swagger-ui URL constructed from publicPathPrefix='${c.publicPathPrefix}': $swaggerUiUrl")) *>
          InternalServerError("Misconfigured swagger-ui URL")
      }
  }

  private def basicAuth(expectedUser: String, expectedPassword: String)(routes: HttpRoutes[IO]): HttpRoutes[IO] = {
    val unauthorized: Response[IO] =
      Response[IO](Status.Unauthorized).putHeaders(Header.Raw(CIString("WWW-Authenticate"), "Basic realm=\"docs\""))
    Kleisli { (req: Request[IO]) =>
      val ok = req.headers.get[Authorization].exists {
        case Authorization(Credentials.Token(AuthScheme.Basic, token)) =>
          Try(new String(Base64.getDecoder.decode(token), "UTF-8")).toOption.exists { decoded =>
            decoded.split(":", 2) match {
              case Array(u, p) => u == expectedUser && p == expectedPassword
              case _           => false
            }
          }
        case _ => false
      }
      if (ok) routes(req) else OptionT.pure[IO](unauthorized)
    }
  }

  // Swagger UI's yaml includes a `servers:` block that Swagger UI uses as the API base URL. We
  // parse, replace with the user-configured `api-public-path-prefix`, then re-serialize so the
  // rendered docs can call the real API directly.
  private def openApiFileContent(c: Config): String = {
    val source = Source.fromFile(s"${c.fileSystemPath}/openapi/openapi.yml")
    try {
      val parser  = new OpenAPIV3Parser
      val openApi = parser.readContents(source.mkString, null, null).getOpenAPI
      val server  = new Server()
      server.setUrl(c.apiPublicPathPrefix)
      openApi.setServers(List(server).asJava)
      Yaml.pretty(openApi)
    } finally {
      source.close()
    }
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

  /** Serve a file from the `swagger-ui` webjar. Returns the file bytes with a best-effort content type guessed from the extension (falls
    * back to `application/octet-stream`). Missing files yield a 404. Classpath reads + byte copying happen inside `IO.blocking` so they
    * don't starve the compute pool under load.
    */
  private def serveSwaggerAsset(relPath: String): IO[Response[IO]] = {
    Try((new WebJarAssetLocator).getFullPath("swagger-ui", relPath)) match {
      case Success(fullPath) =>
        IO.blocking(Option(getClass.getClassLoader.getResourceAsStream(fullPath))).flatMap {
          case None     => NotFound()
          case Some(is) =>
            IO.blocking(readAllBytes(is)).guarantee(IO.blocking(is.close())).flatMap { bytes =>
              val ct = mediaTypeFromExtension(relPath)
              Ok(bytes).map(r => r.withContentType(`Content-Type`(ct)))
            }
        }
      case Failure(_: IllegalArgumentException) => NotFound()
      case Failure(e)                           =>
        IO(log.warn(s"Failed to serve swagger-ui asset '$relPath'", e)) *> InternalServerError("Failed to serve swagger-ui asset")
    }
  }

  // Simple copy-through using a fixed buffer. Avoids `InputStream.readAllBytes` which isn't
  // visible in this build's compile-time stub despite being runtime-available on Java 11.
  private def readAllBytes(is: InputStream): Array[Byte] = {
    val out  = new ByteArrayOutputStream()
    val buf  = new Array[Byte](8192)
    var read = is.read(buf)
    while (read != -1) {
      out.write(buf, 0, read)
      read = is.read(buf)
    }
    out.toByteArray
  }

  private def mediaTypeFromExtension(path: String): MediaType = {
    val lower = path.toLowerCase(java.util.Locale.ROOT)
    if (lower.endsWith(".html")) MediaType.text.html
    else if (lower.endsWith(".css")) MediaType.text.css
    else if (lower.endsWith(".js")) MediaType.application.javascript
    else if (lower.endsWith(".json")) MediaType.application.json
    else if (lower.endsWith(".png")) MediaType.image.png
    else if (lower.endsWith(".svg")) MediaType.image.`svg+xml`
    else if (lower.endsWith(".map")) MediaType.application.json
    else MediaType.application.`octet-stream`
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
