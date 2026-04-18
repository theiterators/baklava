package pl.iterators.baklava

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.Try

case class BaklavaSecuritySerializable(
    httpBearer: Option[HttpBearer] = None,
    httpBasic: Option[HttpBasic] = None,
    apiKeyInHeader: Option[ApiKeyInHeader] = None,
    apiKeyInQuery: Option[ApiKeyInQuery] = None,
    apiKeyInCookie: Option[ApiKeyInCookie] = None,
    mutualTls: Option[MutualTls] = None,
    openIdConnectInBearer: Option[OpenIdConnectInBearer] = None,
    openIdConnectInCookie: Option[OpenIdConnectInCookie] = None,
    oAuth2InBearer: Option[OAuth2InBearer] = None,
    oAuth2InCookie: Option[OAuth2InCookie] = None
) extends Serializable {
  def `type`: Option[String] =
    Seq(
      httpBearer.map(_.`type`),
      httpBasic.map(_.`type`),
      apiKeyInHeader.map(_.`type`),
      apiKeyInQuery.map(_.`type`),
      apiKeyInCookie.map(_.`type`),
      mutualTls.map(_.`type`),
      openIdConnectInBearer.map(_.`type`),
      openIdConnectInCookie.map(_.`type`),
      oAuth2InBearer.map(_.`type`),
      oAuth2InCookie.map(_.`type`)
    ).flatten.headOption

  def descriptionParsed: Option[String] = {
    val descriptions = Seq(
      httpBearer.flatMap(_.descriptionParsed),
      httpBasic.flatMap(_.descriptionParsed),
      apiKeyInHeader.flatMap(_.descriptionParsed),
      apiKeyInQuery.flatMap(_.descriptionParsed),
      apiKeyInCookie.flatMap(_.descriptionParsed),
      mutualTls.flatMap(_.descriptionParsed),
      openIdConnectInBearer.flatMap(_.descriptionParsed),
      openIdConnectInCookie.flatMap(_.descriptionParsed),
      oAuth2InBearer.flatMap(_.descriptionParsed),
      oAuth2InCookie.flatMap(_.descriptionParsed)
    )
    descriptions.flatten.headOption
  }
}

object BaklavaSecuritySerializable {
  def apply(security: Security): BaklavaSecuritySerializable = {
    security match {
      case httpBearer: HttpBearer =>
        BaklavaSecuritySerializable(httpBearer = Some(httpBearer))

      case httpBasic: HttpBasic =>
        BaklavaSecuritySerializable(httpBasic = Some(httpBasic))

      case apiKeyInHeader: ApiKeyInHeader =>
        BaklavaSecuritySerializable(apiKeyInHeader = Some(apiKeyInHeader))

      case apiKeyInQuery: ApiKeyInQuery =>
        BaklavaSecuritySerializable(apiKeyInQuery = Some(apiKeyInQuery))

      case apiKeyInCookie: ApiKeyInCookie =>
        BaklavaSecuritySerializable(apiKeyInCookie = Some(apiKeyInCookie))

      case mutualTls: MutualTls =>
        BaklavaSecuritySerializable(mutualTls = Some(mutualTls))

      case openIdConnectInBearer: OpenIdConnectInBearer =>
        BaklavaSecuritySerializable(openIdConnectInBearer = Some(openIdConnectInBearer))

      case openIdConnectInCookie: OpenIdConnectInCookie =>
        BaklavaSecuritySerializable(openIdConnectInCookie = Some(openIdConnectInCookie))

      case oAuth2InBearer: OAuth2InBearer =>
        BaklavaSecuritySerializable(oAuth2InBearer = Some(oAuth2InBearer))

      case oAuth2InCookie: OAuth2InCookie =>
        BaklavaSecuritySerializable(oAuth2InCookie = Some(oAuth2InCookie))

      case _ =>
        throw new IllegalArgumentException("Unknown Security Type")
    }
  }
}

case class BaklavaSecuritySchemaSerializable(name: String, security: BaklavaSecuritySerializable) extends Serializable

object BaklavaSecuritySchemaSerializable {
  def apply(s: SecurityScheme): BaklavaSecuritySchemaSerializable =
    BaklavaSecuritySchemaSerializable(s.name, BaklavaSecuritySerializable(s.security))
}

case class BaklavaSchemaSerializable(
    className: String,
    `type`: SchemaType,
    format: Option[String],
    properties: Map[String, BaklavaSchemaSerializable],
    items: Option[BaklavaSchemaSerializable],
    `enum`: Option[Set[String]],
    required: Boolean,
    additionalProperties: Boolean,
    default: Option[String],
    description: Option[String]
) extends Serializable

object BaklavaSchemaSerializable {
  def apply[T](schema: Schema[T]): BaklavaSchemaSerializable = {
    BaklavaSchemaSerializable(
      className = schema.className,
      `type` = schema.`type`,
      format = schema.format,
      properties = schema.properties.map { case (k, v) => k -> BaklavaSchemaSerializable(v) },
      items = schema.items.map(BaklavaSchemaSerializable(_)),
      `enum` = schema.`enum`,
      required = schema.required,
      additionalProperties = schema.additionalProperties,
      default = schema.default.map(_.toString),
      description = schema.description
    )
  }
}

case class BaklavaHeaderSerializable(
    name: String,
    description: Option[String],
    schema: BaklavaSchemaSerializable,
    example: Option[String] = None
) extends Serializable
object BaklavaHeaderSerializable {
  def apply[T](h: Header[T]): BaklavaHeaderSerializable =
    BaklavaHeaderSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema))
  def apply[T](h: Header[T], example: Option[String]): BaklavaHeaderSerializable =
    BaklavaHeaderSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema), example)
}

case class BaklavaPathParamSerializable(
    name: String,
    description: Option[String],
    schema: BaklavaSchemaSerializable,
    example: Option[String] = None
) extends Serializable

object BaklavaPathParamSerializable {
  def apply[T](h: PathParam[T]): BaklavaPathParamSerializable =
    BaklavaPathParamSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema))
  def apply[T](h: PathParam[T], example: Option[String]): BaklavaPathParamSerializable =
    BaklavaPathParamSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema), example)
}

case class BaklavaQueryParamSerializable(
    name: String,
    description: Option[String],
    schema: BaklavaSchemaSerializable,
    example: Option[String] = None
) extends Serializable
object BaklavaQueryParamSerializable {
  def apply[T](h: QueryParam[T]): BaklavaQueryParamSerializable =
    BaklavaQueryParamSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema))
  def apply[T](h: QueryParam[T], example: Option[String]): BaklavaQueryParamSerializable =
    BaklavaQueryParamSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema), example)
}

case class BaklavaRequestContextSerializable(
    symbolicPath: String,
    path: String,
    pathDescription: Option[String],
    pathSummary: Option[String],
    method: Option[BaklavaHttpMethod],
    operationDescription: Option[String],
    operationSummary: Option[String],
    operationId: Option[String],
    operationTags: Seq[String],
    securitySchemes: Seq[BaklavaSecuritySchemaSerializable],
    // body: Option[Body], todo on all commented out types
    bodySchema: Option[BaklavaSchemaSerializable],
    // headers: BaklavaHttpHeaders,
    // headersDefinition: Headers,
    // headersProvided: HeadersProvided,
    headersSeq: Seq[BaklavaHeaderSerializable],
    // security: AppliedSecurity,
    // pathParameters: PathParameters,
    // pathParametersProvided: PathParametersProvided,
    pathParametersSeq: Seq[BaklavaPathParamSerializable],
    // queryParameters: QueryParameters,
    // queryParametersProvided: QueryParametersProvided,
    queryParametersSeq: Seq[BaklavaQueryParamSerializable],
    responseDescription: Option[String],
    responseHeaders: Seq[BaklavaHeaderSerializable]
) extends Serializable

object BaklavaRequestContextSerializable {
  def apply(c: BaklavaRequestContext[_, _, _, _, _, _, _]): BaklavaRequestContextSerializable = {
    val pathParamValues  = extractPathParamValues(c.symbolicPath, c.path)
    val queryParamValues = extractQueryParamValues(c.path)

    BaklavaRequestContextSerializable(
      symbolicPath = c.symbolicPath,
      path = c.path,
      pathDescription = c.pathDescription,
      pathSummary = c.pathSummary,
      method = c.method,
      operationDescription = c.operationDescription,
      operationSummary = c.operationSummary,
      operationId = c.operationId,
      operationTags = c.operationTags,
      securitySchemes = c.securitySchemes.map(s => BaklavaSecuritySchemaSerializable(s)),
      bodySchema = c.bodySchema.filter(_ != Schema.emptyBodySchema).map(s => BaklavaSchemaSerializable(s)),
      headersSeq = c.headersSeq.map { h =>
        BaklavaHeaderSerializable(h, caseInsensitiveHeaderValue(c.headers.headers, h.name))
      },
      pathParametersSeq = c.pathParametersSeq.map { p =>
        BaklavaPathParamSerializable(p, pathParamValues.get(p.name))
      },
      queryParametersSeq = c.queryParametersSeq.map { p =>
        BaklavaQueryParamSerializable(p, queryParamValues.get(p.name))
      },
      responseDescription = c.responseDescription,
      responseHeaders = c.responseHeaders.map(h => BaklavaHeaderSerializable(h))
    )
  }

  /** Strip any `#fragment` and `?query` from a resolved path, in that order. */
  private def stripFragmentAndQuery(resolvedPath: String): String =
    resolvedPath.split('#').headOption.getOrElse(resolvedPath).split('?').headOption.getOrElse("")

  /** Strip any `#fragment` from a resolved path. */
  private def stripFragment(resolvedPath: String): String =
    resolvedPath.split('#').headOption.getOrElse(resolvedPath)

  /** Extract `{name} -> actualValue` from a resolved URL by matching against its symbolic template. Both inputs are split on `/`; the
    * resolved path is stripped of its fragment and query string first. A segment `{name}` in the template maps to whatever appears in the
    * same position in the resolved path. URL-decoding is applied to the extracted values so `%20` etc. round-trip.
    */
  private def extractPathParamValues(symbolicPath: String, resolvedPath: String): Map[String, String] = {
    val pathOnly      = stripFragmentAndQuery(resolvedPath)
    val templateParts = symbolicPath.split('/')
    val resolvedParts = pathOnly.split('/')
    if (templateParts.length != resolvedParts.length) Map.empty
    else
      templateParts
        .zip(resolvedParts)
        .collect {
          case (t, r) if t.startsWith("{") && t.endsWith("}") =>
            val name = t.substring(1, t.length - 1)
            name -> java.net.URLDecoder.decode(r, "UTF-8")
        }
        .toMap
  }

  /** Parse a URL query string into `name -> value` pairs. The resolved path is stripped of its `#fragment` first so a trailing fragment
    * doesn't leak into the last query value. If a key appears multiple times (e.g. `?tag=a&tag=b`), the values are joined with commas —
    * matching OpenAPI's default explode=true representation reasonably well for a single example value.
    */
  private def extractQueryParamValues(resolvedPath: String): Map[String, String] = {
    val queryPart = stripFragment(resolvedPath).split('?').drop(1).headOption.getOrElse("")
    if (queryPart.isEmpty) Map.empty
    else
      queryPart
        .split('&')
        .filter(_.nonEmpty)
        .toSeq
        .map { kv =>
          val eq = kv.indexOf('=')
          if (eq < 0) java.net.URLDecoder.decode(kv, "UTF-8")           -> ""
          else java.net.URLDecoder.decode(kv.substring(0, eq), "UTF-8") -> java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8")
        }
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2).mkString(","))
        .toMap
  }

  private def caseInsensitiveHeaderValue(headers: Map[String, String], name: String): Option[String] = {
    val lowered = name.toLowerCase
    headers.find(_._1.toLowerCase == lowered).map(_._2)
  }
}

case class BaklavaResponseContextSerializable(
    protocol: BaklavaHttpProtocol,
    status: BaklavaHttpStatus,
    headers: BaklavaHttpHeaders,
    // body: ResponseBody, //maybe byte array? or maybe not needed
    // rawRequest: RequestType,//todo probably not needed
    requestBodyString: String,
    // rawResponse: ResponseType, //todo probably not needed
    responseBodyString: String,
    requestContentType: Option[String],
    responseContentType: Option[String],
    bodySchema: Option[BaklavaSchemaSerializable]
) extends Serializable

object BaklavaResponseContextSerializable {
  def apply(c: BaklavaResponseContext[_, _, _]): BaklavaResponseContextSerializable =
    BaklavaResponseContextSerializable(
      protocol = c.protocol,
      status = c.status,
      headers = c.headers,
      requestBodyString = c.requestBodyString,
      responseBodyString = c.responseBodyString,
      requestContentType = c.requestContentType,
      responseContentType = c.responseContentType,
      bodySchema = c.bodySchema.filter(_ != Schema.emptyBodySchema).map(s => BaklavaSchemaSerializable(s))
    )
}

case class BaklavaSerializableCall(request: BaklavaRequestContextSerializable, response: BaklavaResponseContextSerializable)
    extends Serializable

object BaklavaSerialize {

  private val dirName       = "target/baklava/calls"
  private val dirFile       = new File(dirName)
  private val fileExtension = "json"

  // JSON codec for BaklavaSerializableCall
  implicit val serializableCallCodec: JsonValueCodec[BaklavaSerializableCall] = JsonCodecMaker.make(
    CodecMakerConfig.withAllowRecursiveTypes(true)
  )

  def serializeCall(
      request: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?],
      response: BaklavaResponseContext[?, ?, ?]
  ): Try[Unit] = {
    for {
      _ <- Try(dirFile.mkdirs())
      context   = BaklavaSerializableCall(BaklavaRequestContextSerializable(request), BaklavaResponseContextSerializable(response))
      jsonBytes = writeToArray(context)
      hashBytes = MessageDigest.getInstance("SHA-256").digest(jsonBytes)
      chunkName = Base64.getUrlEncoder.encodeToString(hashBytes).replaceAll("=", "")
      chunkFile = new File(s"$dirName/$chunkName.$fileExtension")
      _ <- Try(Files.write(chunkFile.toPath, jsonBytes))
    } yield ()
  }

  def listSerializedCalls(): Try[Seq[BaklavaSerializableCall]] = {
    Try {
      dirFile.mkdirs()
      Option(dirFile.listFiles())
        .getOrElse(Array.empty[File])
        .filter(_.getName.endsWith(fileExtension))
        .flatMap(readCallFromFile)
        .toSeq
    }
  }

  private def readCallFromFile(file: File): Option[BaklavaSerializableCall] = {
    Try {
      val jsonBytes = Files.readAllBytes(file.toPath)
      readFromArray[BaklavaSerializableCall](jsonBytes)
    }.toOption
  }

  def cleanSerializedCalls(): Try[Unit] = {
    val dir: Path = Paths.get(dirFile.getPath)

    if (Files.exists(dir) && Files.isDirectory(dir)) {
      Try {
        Files
          .walk(dir)
          .sorted(implicitly[Ordering[Path]].reverse)
          .iterator()
          .asScala
          .foreach(Files.delete)
      }
    } else {
      Try(())
    }
  }

}
