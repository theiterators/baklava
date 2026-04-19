package pl.iterators.baklava.http4s.routes

import cats.data.Kleisli
import cats.effect.IO
import com.typesafe.config.{Config => TypesafeConfig}
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.{Location, `Content-Type`}
import org.http4s.server.middleware.authentication.BasicAuth
import org.webjars.WebJarAssetLocator

import java.io.FileNotFoundException
import scala.io.Source
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Try, Using}

object BaklavaRoutes {

  private lazy val swaggerVersion: String =
    Option(new WebJarAssetLocator().getWebJars.get("swagger-ui")).getOrElse(
      throw new IllegalStateException(
        "swagger-ui webjar not on the classpath — add `\"org.webjars\" % \"swagger-ui\" % \"...\"` to your project's dependencies " +
          "or remove baklava-http4s-routes if you don't intend to serve SwaggerUI."
      )
    )

  private def withTrailingSlash(prefix: String): String =
    if (prefix.endsWith("/")) prefix else prefix + "/"

  def routes(config: TypesafeConfig): HttpRoutes[IO] = {
    val internalConfig = Config(config)
    if (!internalConfig.enabled) HttpRoutes.empty[IO]
    else
      (internalConfig.basicAuthUser, internalConfig.basicAuthPassword) match {
        case (Some(user), Some(password)) =>
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
      IO.blocking(openApiFileContent(c))
        .flatMap(content => Ok(content).map(_.withContentType(`Content-Type`(MediaType.text.yaml))))
        .recoverWith { case _: FileNotFoundException =>
          NotFound("openapi document not available — run `sbt test` first to generate it")
        }

    case GET -> Root / "swagger-ui" / version / "swagger-initializer.js" if version == swaggerVersion =>
      Ok(swaggerInitializerContent(c)).map(_.withContentType(`Content-Type`(MediaType.application.javascript)))

    case req @ GET -> "swagger-ui" /: rest =>
      val relPath = rest.segments.map(_.decoded()).mkString("/")
      Try((new WebJarAssetLocator).getFullPath("swagger-ui", relPath)).toOption match {
        case Some(fullPath) => StaticFile.fromResource(fullPath, Some(req)).getOrElseF(NotFound())
        case None           => NotFound()
      }

    case GET -> Root / "swagger" =>
      val swaggerUiUrl = s"${withTrailingSlash(c.publicPathPrefix)}swagger-ui/$swaggerVersion/index.html"
      IO.fromEither(Uri.fromString(swaggerUiUrl)).flatMap(uri => SeeOther(Location(uri)))
  }

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
    def apply(config: TypesafeConfig): Config = {
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
