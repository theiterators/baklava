package pl.iterators.baklava.routes

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.parser.OpenAPIV3Parser
import org.apache.pekko.http.scaladsl.model.headers.Location
import org.apache.pekko.http.scaladsl.model.{ContentType, HttpCharsets, HttpEntity, HttpResponse, MediaType, MediaTypes, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.server.directives.{Credentials, RouteDirectives}
import org.webjars.WebJarAssetLocator

import java.io.FileNotFoundException
import scala.io.Source
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Failure, Success, Try, Using}

object BaklavaRoutes {

  private lazy val swaggerVersion: String =
    Option(new WebJarAssetLocator().getWebJars.get("swagger-ui")).getOrElse(
      throw new IllegalStateException(
        "swagger-ui webjar not on the classpath — add `\"org.webjars\" % \"swagger-ui\" % \"...\"` to your project's dependencies " +
          "or remove baklava-pekko-http-routes if you don't intend to serve SwaggerUI."
      )
    )

  private val yamlContentType: ContentType.WithFixedCharset =
    MediaType.customWithFixedCharset("application", "yaml", HttpCharsets.`UTF-8`).toContentType

  private val javascriptContentType: ContentType.NonBinary =
    MediaTypes.`application/javascript`.toContentType(HttpCharsets.`UTF-8`)

  private def withTrailingSlash(prefix: String): String =
    if (prefix.endsWith("/")) prefix else prefix + "/"

  def routes(config: BaklavaRoutesConfig = BaklavaRoutesConfig.fromEnv): Route =
    if (config.enabled)
      authenticateBasic("docs", basicAuthOpt(config)) { _ =>
        pathPrefix("docs") {
          pathSingleSlash {
            getFromFile(s"${config.fileSystemPath}/simple/index.html")
          } ~ getFromDirectory(s"${config.fileSystemPath}/simple")
        } ~ path("openapi") {
          Try(openApiFileContent(config)) match {
            case Success(yaml)                     => complete(HttpEntity(yamlContentType, yaml))
            case Failure(_: FileNotFoundException) =>
              complete(StatusCodes.NotFound -> "openapi document not available — run `sbt test` first to generate it")
            case Failure(e) => failWith(e)
          }
        } ~ (path("swagger-ui" / swaggerVersion / "swagger-initializer.js") & get) {
          complete(HttpEntity(javascriptContentType, swaggerInitializerContent(config)))
        } ~ pathPrefix("swagger-ui") {
          swaggerWebJar
        } ~ pathPrefix("swagger") {
          get(complete(swaggerRedirectHttpResponse(config)))
        }
      }
    else
      RouteDirectives.reject

  def routes(config: com.typesafe.config.Config): Route =
    routes(BaklavaRoutesConfig.fromTypesafeConfig(config))

  private def basicAuthOpt(config: BaklavaRoutesConfig)(credentials: Credentials): Option[String] =
    (config.basicAuthUser, config.basicAuthPassword) match {
      case (Some(user), Some(password)) =>
        credentials match {
          case p @ Credentials.Provided(id) if id == user && p.verify(password) => Some(id)
          case _                                                                => None
        }
      case _ => Some("")
    }

  private def openApiFileContent(config: BaklavaRoutesConfig): String =
    Using.resource(Source.fromFile(s"${config.fileSystemPath}/openapi/openapi.yml")) { source =>
      val parser  = new OpenAPIV3Parser
      val openApi = parser.readContents(source.mkString, null, null).getOpenAPI
      val server  = new Server()
      server.setUrl(config.apiPublicPathPrefix)
      openApi.setServers(List(server).asJava)
      Yaml.pretty(openApi)
    }

  private def swaggerInitializerContent(config: BaklavaRoutesConfig): String = {
    val swaggerDocsUrl = s"${withTrailingSlash(config.publicPathPrefix)}openapi"

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

  private def swaggerRedirectHttpResponse(config: BaklavaRoutesConfig): HttpResponse = {
    val swaggerUiUrl = s"${withTrailingSlash(config.publicPathPrefix)}swagger-ui/${swaggerVersion}/index.html"
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
}
