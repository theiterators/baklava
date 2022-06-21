package pl.iterators.baklava.routes

import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.{Credentials, RouteDirectives}
import org.webjars.WebJarAssetLocator

import scala.io.Source
import scala.util.{Failure, Success, Try}

object BaklavaRoutes {
  def routes(config: com.typesafe.config.Config): Route = {
    implicit val internalConfig: BaklavaRoutes.Config = BaklavaRoutes.Config(config)
    if (internalConfig.enabled)
      authenticateBasic("docs", basicAuthOpt) { _ =>
        pathPrefix("docs") {
          pathSingleSlash {
            getFromFile(s"${internalConfig.fileSystemPath}/simple/index.html")
          } ~ getFromDirectory(s"${internalConfig.fileSystemPath}/simple")
        } ~ path("openapi") {
          complete(openApiFileContent)
        } ~ pathPrefix("swagger-ui") {
          swaggerWebJar
        } ~ pathPrefix("swagger") {
          get(complete(swaggerRedirectHttpResponse))
        }
      } else
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
    val source       = Source.fromFile(s"${internalConfig.fileSystemPath}/openapi/openapi.yml")
    val lines        = source.getLines()
    val firstLine    = lines.next()
    val serverConfig = List("servers:", s"  - url: ${internalConfig.apiPublicPathPrefix}")
    val tailLines    = lines.toList
    val content      = firstLine :: serverConfig ::: tailLines
    source.close()
    content.mkString("\n")
  }

  private def swaggerRedirectHttpResponse(implicit internalConfig: BaklavaRoutes.Config) = {
    val swaggerUiUrl   = s"${internalConfig.publicPathPrefix}swagger-ui/3.52.5/index.html"
    val swaggerDocsUrl = s"${internalConfig.publicPathPrefix}openapi"
    HttpResponse(status = StatusCodes.SeeOther, headers = Location(s"$swaggerUiUrl?url=$swaggerDocsUrl&layout=BaseLayout") :: Nil)
  }

  private lazy val swaggerWebJar: Route = {
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

  private case class Config(enabled: Boolean,
                            basicAuthUser: Option[String],
                            basicAuthPassword: Option[String],
                            fileSystemPath: String,
                            publicPathPrefix: String,
                            apiPublicPathPrefix: String)

  private object Config {
    def apply(config: com.typesafe.config.Config): Config = {
      val c = config.getConfig("baklavaRoutes")
      Config(
        enabled = c.getBoolean("enabled"),
        basicAuthUser = Try(c.getString("basicAuthUser")).toOption,
        basicAuthPassword = Try(c.getString("basicAuthPassword")).toOption,
        fileSystemPath = c.getString("fileSystemPath"),
        publicPathPrefix = c.getString("publicPathPrefix"),
        apiPublicPathPrefix = c.getString("apiPublicPathPrefix")
      )
    }
  }
}
