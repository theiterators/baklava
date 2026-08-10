package pl.iterators.baklava.sttp4

import io.circe.{Decoder, Encoder, Printer}
import pl.iterators.baklava.{
  BaklavaAssertionException,
  BaklavaHttpDsl,
  BaklavaHttpProtocol,
  BaklavaRequestContext,
  BaklavaResponseContext,
  BaklavaTestFrameworkDsl,
  EmptyBody,
  EmptyBodyInstance,
  FormOf,
  Multipart => BaklavaMultipart
}
import sttp.client4.{asByteArrayAlways, emptyRequest, ByteArrayBody, DefaultSyncBackend, Request, Response, StringBody, SyncBackend}
import sttp.model.{Header => SttpHeader, MediaType, Method, StatusCode, Uri}

import java.nio.charset.StandardCharsets.UTF_8
import scala.reflect.ClassTag

// lower priority than BaklavaSttp's specific instances, so ToSttpBody[String] beats Encoder[String]-derived JSON
trait BaklavaSttpLowPriorityBodies {
  implicit def circeEncoderToSttpBody[T](implicit encoder: Encoder[T]): ToSttpBody[T] =
    t => Some(SttpBodyContent(Printer.noSpaces.print(encoder(t)).getBytes(UTF_8), "application/json"))

  implicit def circeDecoderFromSttpBody[T](implicit decoder: Decoder[T]): FromSttpBody[T] =
    bytes => io.circe.parser.decode[T](new String(bytes, UTF_8))
}

trait BaklavaSttp[TestFrameworkFragmentType, TestFrameworkFragmentsType, TestFrameworkExecutionType[_]]
    extends BaklavaSttpLowPriorityBodies
    with BaklavaHttpDsl[
      SyncBackend,
      ToSttpBody,
      FromSttpBody,
      TestFrameworkFragmentType,
      TestFrameworkFragmentsType,
      TestFrameworkExecutionType
    ] {
  this: BaklavaTestFrameworkDsl[
    SyncBackend,
    ToSttpBody,
    FromSttpBody,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  override type HttpResponse   = Response[Array[Byte]]
  override type HttpRequest    = Request[Array[Byte]]
  override type HttpProtocol   = BaklavaHttpProtocol
  override type HttpStatusCode = StatusCode
  override type HttpMethod     = Method
  override type HttpHeaders    = Seq[SttpHeader]

  // root of the documented API; ctx.path is appended to it on every request
  def baseUri: Uri

  // added to every request; per-test headers win on name conflict; Content-Type is rejected — declare it per request
  def defaultHeaders: Seq[SttpHeader] = Seq.empty

  // override to customize the client (proxies, TLS, auth wrappers)
  lazy val defaultBackend: SyncBackend = DefaultSyncBackend()

  // remote services always send undeclared headers, so strict mode would never pass
  def strictHeaderCheckDefault: Boolean = false

  override implicit protected def emptyToRequestBodyType: ToSttpBody[EmptyBody] = _ => None

  override implicit protected def formUrlencodedToRequestBodyType[T]: ToSttpBody[FormOf[T]] =
    form => Some(SttpBodyContent(SttpBodies.urlEncodeForm(form.fields).getBytes(UTF_8), "application/x-www-form-urlencoded"))

  override implicit protected def multipartToRequestBodyType: ToSttpBody[BaklavaMultipart] =
    multipart => Some(SttpBodyContent(SttpBodies.renderMultipart(multipart), SttpBodies.multipartContentType))

  implicit def stringToSttpBody: ToSttpBody[String] =
    s => Some(SttpBodyContent(s.getBytes(UTF_8), "text/plain; charset=UTF-8"))

  implicit def byteArrayToSttpBody: ToSttpBody[Array[Byte]] =
    bytes => Some(SttpBodyContent(bytes, "application/octet-stream"))

  // core itself asserts emptiness when EmptyBody is the expected response
  override implicit protected def emptyToResponseBodyType: FromSttpBody[EmptyBody] = _ => Right(EmptyBodyInstance)

  implicit def stringFromSttpBody: FromSttpBody[String] = bytes => Right(new String(bytes, UTF_8))

  implicit def byteArrayFromSttpBody: FromSttpBody[Array[Byte]] = bytes => Right(bytes)

  // wire types already are the sttp.model types core speaks — all conversions are identities
  override implicit def statusCodeToBaklavaStatusCodes(statusCode: StatusCode): StatusCode                    = statusCode
  override implicit def baklavaStatusCodeToStatusCode(status: StatusCode): StatusCode                         = status
  override implicit def httpMethodToBaklavaHttpMethod(method: Method): Method                                 = method
  override implicit def baklavaHttpMethodToHttpMethod(method: Method): Method                                 = method
  override implicit def baklavaHttpProtocolToHttpProtocol(protocol: BaklavaHttpProtocol): BaklavaHttpProtocol = protocol
  override implicit def httpProtocolToBaklavaHttpProtocol(protocol: BaklavaHttpProtocol): BaklavaHttpProtocol = protocol
  override implicit def baklavaHeadersToHttpHeaders(headers: Seq[SttpHeader]): Seq[SttpHeader]                = headers
  override implicit def httpHeadersToBaklavaHeaders(headers: Seq[SttpHeader]): Seq[SttpHeader]                = headers

  override def httpResponseToBaklavaResponseContext[T: FromSttpBody: ClassTag](
      request: HttpRequest,
      response: HttpResponse
  ): BaklavaResponseContext[T, HttpRequest, HttpResponse] = {
    val responseString = new String(response.body, UTF_8)
    val requestString  = request.body match {
      case ByteArrayBody(bytes, _) => new String(bytes, UTF_8)
      case StringBody(s, _, _)     => s
      case _                       => ""
    }
    BaklavaResponseContext(
      // sttp does not expose the negotiated protocol version
      BaklavaHttpProtocol("HTTP/1.1"),
      response.code,
      response.headers,
      implicitly[FromSttpBody[T]].apply(response.body) match {
        case Right(value)    => value
        case Left(exception) =>
          throw new BaklavaAssertionException(
            s"Failed to decode response body: ${exception.getMessage}\n" +
              s"Expected: ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}, but got: ${response.code.code} -> ${responseString.take(maxBodyLengthInAssertion)}"
          )
      },
      request,
      requestString,
      response,
      responseString,
      request.headers.find(_.name.equalsIgnoreCase("Content-Type")).map(_.value),
      response.contentType
    )
  }

  override def baklavaContextToHttpRequest[
      RequestBody,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided,
      Headers_,
      HeadersProvided
  ](
      ctx: BaklavaRequestContext[
        RequestBody,
        PathParameters,
        PathParametersProvided,
        QueryParameters,
        QueryParametersProvided,
        Headers_,
        HeadersProvided
      ]
  )(implicit
      requestBody: ToSttpBody[RequestBody]
  ): HttpRequest = {
    val parsedOverride = findContentTypeOverride(ctx.headers)
    val declared       = if (parsedOverride.isDefined) dropContentType(ctx.headers) else ctx.headers
    val defaults       = defaultHeaders
    if (defaults.exists(_.name.equalsIgnoreCase("Content-Type")))
      throw new IllegalArgumentException(
        "Content-Type must not be set in defaultHeaders — declare it per request in the test's headers instead"
      )
    val merged                     = declared ++ defaults.filterNot(d => declared.exists(_.name.equalsIgnoreCase(d.name)))
    val base: Request[Array[Byte]] = emptyRequest
      .method(ctx.method.get, BaklavaSttp.resolveUri(baseUri, ctx.path))
      .headers(merged*)
      .response(asByteArrayAlways)
    ctx.body.flatMap(requestBody(_)) match {
      case Some(SttpBodyContent(bytes, contentType)) => base.body(bytes).contentType(parsedOverride.getOrElse(contentType))
      case None                                      => parsedOverride.fold(base)(ct => base.contentType(ct))
    }
  }

  // Throws on either multiple declarations or an unparseable value — both are test-authoring bugs.
  private def findContentTypeOverride(hs: Seq[SttpHeader]): Option[String] = {
    val cts = hs.filter(_.name.toLowerCase(java.util.Locale.ROOT) == "content-type")
    cts match {
      case Seq()       => None
      case Seq(single) =>
        MediaType.parse(single.value) match {
          case Right(_)    => Some(single.value)
          case Left(error) =>
            throw new IllegalArgumentException(s"Could not parse declared Content-Type header '${single.value}': $error")
        }
      case multiple =>
        throw new IllegalArgumentException(
          s"Multiple Content-Type headers declared on one request: [${multiple.map(_.value).mkString(", ")}]. " +
            "Declare a single Content-Type or none at all."
        )
    }
  }

  private def dropContentType(hs: Seq[SttpHeader]): Seq[SttpHeader] =
    hs.filterNot(_.name.toLowerCase(java.util.Locale.ROOT) == "content-type")

  override def performRequest(backend: SyncBackend, request: HttpRequest): HttpResponse =
    request.send(backend)
}

object BaklavaSttp {
  def resolveUri(baseUri: Uri, path: String): Uri = {
    // a query/fragment on the base would end up embedded mid-URI — always a spec-authoring bug
    if (baseUri.querySegments.nonEmpty || baseUri.fragmentSegment.isDefined)
      throw new IllegalArgumentException(s"baseUri must not carry a query or fragment: $baseUri")
    Uri
      .parse(baseUri.toString.stripSuffix("/") + path)
      .fold(error => throw new IllegalArgumentException(s"Could not build request URI: $error"), identity)
  }
}
