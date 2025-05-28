package pl.iterators.baklava.routes

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.apache.pekko.http.scaladsl.model.{HttpResponse, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.directives.{Credentials, RouteDirectives}
import org.webjars.WebJarAssetLocator

import scala.io.Source
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Failure, Success, Try}

object BaklavaRoutes {
  private val swaggerVersion = "5.17.11"

  def routes(config: com.typesafe.config.Config): Route = {
    implicit val internalConfig: BaklavaRoutes.Config = BaklavaRoutes.Config(config)
    if (internalConfig.enabled)
      authenticateBasic("docs", basicAuthOpt) { _ =>
        /* TODO uncomment after introduce simple formatter
        pathPrefix("docs") {
          pathSingleSlash {
            getFromFile(s"${internalConfig.fileSystemPath}/simple/index.html")
          } ~ getFromDirectory(s"${internalConfig.fileSystemPath}/simple")
        } ~ */
        path("openapi") {
          complete(openApiFileContent)
        } ~ (path("swagger-ui" / swaggerVersion / "swagger-initializer.js") & get) {
          complete(swaggerInitializerContent)
        } ~ pathPrefix("swagger-ui") {
          swaggerWebJar
        } ~ pathPrefix("swagger") {
          get(complete(swaggerRedirectHttpResponse))
        }
      }
    else
      RouteDirectives.reject
  }

  private def basicAuthOpt(credentials: Credentials)(implicit internalConfig: BaklavaRoutes.Config): Option[String] =
    (internalConfig.basicAuthUser, internalConfig.basicAuthPassword) match {
      case (Some(user), Some(password)) =>
        credentials match {
          case p @ Credentials.Provided(id) if id == user && p.verify(password) => Some(id)
          case _                                                                => None
        }
      case _ => Some("")
    }

  private def openApiFileContent(implicit internalConfig: BaklavaRoutes.Config): String = {
    val parser = new OpenAPIV3Parser
    val source = Source.fromFile(s"${internalConfig.fileSystemPath}/openapi/openapi.yml")
    try {
      val openApi = parser.readContents(source.mkString, null, null).getOpenAPI
      val server  = new Server()
      server.setUrl(internalConfig.apiPublicPathPrefix)
      openApi.setServers(List(server).asJava)
      Yaml.pretty(openApi)
    } finally {
      source.close()
    }
  }

  private def swaggerInitializerContent(implicit internalConfig: BaklavaRoutes.Config): String = {
    val swaggerDocsUrl = s"${internalConfig.publicPathPrefix}openapi"

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

  private def swaggerRedirectHttpResponse(implicit internalConfig: BaklavaRoutes.Config) = {
    val swaggerUiUrl = s"${internalConfig.publicPathPrefix}swagger-ui/${swaggerVersion}/index.html"
    HttpResponse(status = StatusCodes.SeeOther, headers = Location(swaggerUiUrl) :: Nil)
  }

  private lazy val swaggerWebJar: Route =
    extractUnmatchedPath { path =>
      Try((new WebJarAssetLocator).getFullPath("swagger-ui", path.toString)) match {
        case Success(fullPath) =>
          getFromResource(fullPath)
        case Failure(_: IllegalArgumentException) =>
          reject
        case Failure(e) =>
          failWith(e)
      }
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
