package pl.iterators.baklava

import java.io.{ByteArrayOutputStream, File, FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

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
    schema: BaklavaSchemaSerializable
) extends Serializable
object BaklavaHeaderSerializable {
  def apply[T](h: Header[T]): BaklavaHeaderSerializable =
    BaklavaHeaderSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema))
}

case class BaklavaPathParamSerializable(
    name: String,
    description: Option[String],
    schema: BaklavaSchemaSerializable
) extends Serializable

object BaklavaPathParamSerializable {
  def apply[T](h: PathParam[T]): BaklavaPathParamSerializable =
    BaklavaPathParamSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema))
}

case class BaklavaQueryParamSerializable(
    name: String,
    description: Option[String],
    schema: BaklavaSchemaSerializable
) extends Serializable
object BaklavaQueryParamSerializable {
  def apply[T](h: QueryParam[T]): BaklavaQueryParamSerializable =
    BaklavaQueryParamSerializable(h.name, h.description, BaklavaSchemaSerializable(h.schema))
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
  def apply(c: BaklavaRequestContext[_, _, _, _, _, _, _]): BaklavaRequestContextSerializable = BaklavaRequestContextSerializable(
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
    headersSeq = c.headersSeq.map(h => BaklavaHeaderSerializable(h)),
    pathParametersSeq = c.pathParametersSeq.map(p => BaklavaPathParamSerializable(p)),
    queryParametersSeq = c.queryParametersSeq.map(p => BaklavaQueryParamSerializable(p)),
    responseDescription = c.responseDescription,
    responseHeaders = c.responseHeaders.map(h => BaklavaHeaderSerializable(h))
  )
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
  private val fileExtension = "call"

  def serializeCall(
      request: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?],
      response: BaklavaResponseContext[?, ?, ?]
  ): Unit = {
    dirFile.mkdirs()

    val context = BaklavaSerializableCall(BaklavaRequestContextSerializable(request), BaklavaResponseContextSerializable(response))

    val digest    = MessageDigest.getInstance("SHA-256")
    val jsonBytes = serializeToBytes(context)
    val hashBytes = digest.digest(jsonBytes)

    // Generate a unique filename based on the hash
    val chunkName = Base64.getUrlEncoder.encodeToString(hashBytes).replaceAll("=", "")
    val chunkFile = new File(s"$dirName/$chunkName.$fileExtension")

    Using(new ObjectOutputStream(new FileOutputStream(chunkFile))) { outputStream =>
      outputStream.writeObject(context)
    }.recover { case e: Exception =>
      println(s"Failed to write to file: $e")
    }
    ()
  }

  def listSerializedCalls(): Seq[BaklavaSerializableCall] = {
    dirFile.mkdirs()

    Option(dirFile.listFiles())
      .getOrElse(Array.empty[File])
      .filter(_.getName.endsWith(fileExtension))
      .flatMap { file =>
        Using(new ObjectInputStream(new FileInputStream(file))) { inputStream =>
          inputStream.readObject().asInstanceOf[BaklavaSerializableCall]
        }.toOption
      }
      .toSeq
  }

  def cleanSerializedCalls(): Unit = {
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
      ()
    }
  }

  private def serializeToBytes(obj: Any): Array[Byte] = {
    val byteArrayOutputStream = new ByteArrayOutputStream()
    val objectOutputStream    = new ObjectOutputStream(byteArrayOutputStream)
    objectOutputStream.writeObject(obj)
    objectOutputStream.flush()
    byteArrayOutputStream.toByteArray
  }
}
