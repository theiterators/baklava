package pl.iterators.baklava

import com.github.plokhotnyuk.jsoniter_scala.core.*
import com.github.plokhotnyuk.jsoniter_scala.macros.*
import io.circe.Json
import sttp.model.{Header => SttpHeader, Method, StatusCode}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Base64
import scala.jdk.CollectionConverters.*
import scala.util.Try

/** Jsoniter codec for circe `Json` values. Used to round-trip a structured default value (issue #61) through the on-disk serialized call
  * format without stringifying — the old `Option[String]` used `_.toString`, which produced Scala syntax like `"List(1, 2, 3)"` for
  * collections and invalid defaults in the generated OpenAPI.
  *
  * The encode/decode here uses the readRawVal/writeRawVal APIs to preserve arbitrary JSON shape.
  */
private[baklava] object JsonCirceCodec {
  implicit val jsonValueCodec: JsonValueCodec[Json] = new JsonValueCodec[Json] {
    override def decodeValue(in: JsonReader, default: Json): Json = {
      val raw = new String(in.readRawValAsBytes(), StandardCharsets.UTF_8)
      io.circe.parser.parse(raw).fold(throw _, identity)
    }
    override def encodeValue(x: Json, out: JsonWriter): Unit =
      out.writeRawVal(x.noSpaces.getBytes(StandardCharsets.UTF_8))
    override def nullValue: Json = Json.Null
  }
}

/** Option-bag shape (vs. sealed-trait field) so jsoniter's structural codec round-trips without a discriminator tag. */
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

  def toSecurity: Security =
    httpBearer
      .orElse(httpBasic)
      .orElse(apiKeyInHeader)
      .orElse(apiKeyInQuery)
      .orElse(apiKeyInCookie)
      .orElse(mutualTls)
      .orElse(openIdConnectInBearer)
      .orElse(openIdConnectInCookie)
      .orElse(oAuth2InBearer)
      .orElse(oAuth2InCookie)
      .getOrElse(throw new IllegalStateException("BaklavaSecuritySerializable has no Security set"))

  def `type`: Option[String]            = Some(toSecurity.`type`)
  def descriptionParsed: Option[String] = toSecurity.descriptionParsed
}

object BaklavaSecuritySerializable {
  def apply(security: Security): BaklavaSecuritySerializable = security match {
    case s: HttpBearer            => BaklavaSecuritySerializable(httpBearer = Some(s))
    case s: HttpBasic             => BaklavaSecuritySerializable(httpBasic = Some(s))
    case s: ApiKeyInHeader        => BaklavaSecuritySerializable(apiKeyInHeader = Some(s))
    case s: ApiKeyInQuery         => BaklavaSecuritySerializable(apiKeyInQuery = Some(s))
    case s: ApiKeyInCookie        => BaklavaSecuritySerializable(apiKeyInCookie = Some(s))
    case s: MutualTls             => BaklavaSecuritySerializable(mutualTls = Some(s))
    case s: OpenIdConnectInBearer => BaklavaSecuritySerializable(openIdConnectInBearer = Some(s))
    case s: OpenIdConnectInCookie => BaklavaSecuritySerializable(openIdConnectInCookie = Some(s))
    case s: OAuth2InBearer        => BaklavaSecuritySerializable(oAuth2InBearer = Some(s))
    case s: OAuth2InCookie        => BaklavaSecuritySerializable(oAuth2InCookie = Some(s))
    case NoopSecurity             => throw new IllegalArgumentException("NoopSecurity is an internal sentinel and cannot be serialized")
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
    default: Option[Json],
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
      default = schema.default.flatMap(encodeDefault(schema.`type`)),
      description = schema.description
    )
  }

  /** Encode a `Schema[T].default` value as structured JSON rather than `.toString`. Primitive types pick the matching `Json.from…`
    * constructor based on `SchemaType`. Collection/object types have no generic `Encoder[T]` available here, so we fall back to a JSON
    * string (which is still technically valid JSON, just not a useful default). Users with structured defaults on custom types should
    * override by providing their own `BaklavaSchemaSerializable` instead of relying on this fallback — fixes issue #61.
    *
    * `optionSchema` sets `default = Some(None)` so that an Option[T] parameter renders the semantically correct `default: null`. We
    * intercept `None` / `Some(x)` here so that value encoding uses the inner type (the `SchemaType` is inherited from the wrapped T).
    */
  private def encodeDefault[T](schemaType: SchemaType)(value: T): Option[Json] = value match {
    case None        => Some(Json.Null)
    case Some(inner) => encodeDefault(schemaType)(inner)
    case _           => encodePrimitive(schemaType)(value)
  }

  private def encodePrimitive[T](schemaType: SchemaType)(value: T): Option[Json] = schemaType match {
    case SchemaType.NullType    => Some(Json.Null)
    case SchemaType.StringType  => Some(Json.fromString(value.toString))
    case SchemaType.BooleanType =>
      value match {
        case b: Boolean => Some(Json.fromBoolean(b))
        case other      => Some(Json.fromString(other.toString))
      }
    case SchemaType.IntegerType =>
      value match {
        case i: Int   => Some(Json.fromInt(i))
        case l: Long  => Some(Json.fromLong(l))
        case s: Short => Some(Json.fromInt(s.toInt))
        case b: Byte  => Some(Json.fromInt(b.toInt))
        case other    => Json.fromString(other.toString).some
      }
    case SchemaType.NumberType =>
      value match {
        case d: Double      => Some(Json.fromDoubleOrString(d))
        case f: Float       => Some(Json.fromFloatOrString(f))
        case bd: BigDecimal => Some(Json.fromBigDecimal(bd))
        case bi: BigInt     => Some(Json.fromBigInt(bi))
        case other          => Some(Json.fromString(other.toString))
      }
    case SchemaType.ArrayType | SchemaType.ObjectType =>
      // We can't deeply-encode a collection/object without an `Encoder[T]`. The .toString
      // fallback (historical behavior) yields Scala syntax which is valid JSON only by
      // coincidence. Users who need a structured default on a complex type should override
      // `Schema[T]` with a hand-crafted `BaklavaSchemaSerializable`.
      Some(Json.fromString(value.toString))
  }

  // Tiny local `.some` to keep the pattern-match legs uniform on Scala 2.13 without pulling in
  // cats.syntax. (In cats-free code, `Some(x)` is fine too, but I find the shorter form less
  // noisy next to the other Json.from* calls.)
  private implicit class AnyOps[A](val a: A) extends AnyVal {
    def some: Option[A] = Some(a)
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
    method: Option[Method],
    operationDescription: Option[String],
    operationSummary: Option[String],
    operationId: Option[String],
    operationTags: Seq[String],
    securitySchemes: Seq[BaklavaSecuritySchemaSerializable],
    bodySchema: Option[BaklavaSchemaSerializable],
    bodyString: String,
    headersSeq: Seq[BaklavaHeaderSerializable],
    pathParametersSeq: Seq[BaklavaPathParamSerializable],
    queryParametersSeq: Seq[BaklavaQueryParamSerializable],
    responseDescription: Option[String],
    responseHeaders: Seq[BaklavaHeaderSerializable]
) extends Serializable

object BaklavaRequestContextSerializable {
  def apply(
      c: BaklavaRequestContext[_, _, _, _, _, _, _],
      bodyString: String
  ): BaklavaRequestContextSerializable = {
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
      bodyString = bodyString,
      headersSeq = c.headersSeq.map { h =>
        BaklavaHeaderSerializable(h, caseInsensitiveHeaderValue(c.headers, h.name))
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

  private def caseInsensitiveHeaderValue(headers: Seq[SttpHeader], name: String): Option[String] = {
    val lowered = name.toLowerCase(java.util.Locale.ROOT)
    headers.find(_.name.toLowerCase(java.util.Locale.ROOT) == lowered).map(_.value)
  }
}

case class BaklavaResponseContextSerializable(
    protocol: BaklavaHttpProtocol,
    status: StatusCode,
    headers: Seq[SttpHeader],
    bodyString: String,
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
      bodyString = c.responseBodyString,
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

  // JSON codec for BaklavaSerializableCall. The import brings our custom `Json`-typed codec into
  // scope so jsoniter-scala's macro picks it up when walking into `BaklavaSchemaSerializable.default`.
  import JsonCirceCodec.jsonValueCodec
  implicit val serializableCallCodec: JsonValueCodec[BaklavaSerializableCall] = JsonCodecMaker.make(
    CodecMakerConfig.withAllowRecursiveTypes(true)
  )

  def serializeCall(
      request: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?],
      response: BaklavaResponseContext[?, ?, ?]
  ): Try[Unit] = {
    for {
      _ <- Try(dirFile.mkdirs())
      context = BaklavaSerializableCall(
        BaklavaRequestContextSerializable(request, response.requestBodyString),
        BaklavaResponseContextSerializable(response)
      )
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
