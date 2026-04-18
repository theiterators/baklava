package pl.iterators.baklava.http4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import org.http4s.multipart.{Boundary, Part => Http4sPart}
import org.http4s.{
  EntityDecoder,
  EntityEncoder,
  Header,
  Headers,
  HttpRoutes,
  HttpVersion,
  MediaType,
  Method,
  Request,
  Response,
  Status,
  Uri,
  UrlForm,
  headers
}
import org.typelevel.ci.CIString
import pl.iterators.baklava.{
  BaklavaAssertionException,
  BaklavaHttpDsl,
  BaklavaHttpProtocol,
  BaklavaRequestContext,
  BaklavaResponseContext,
  BaklavaTestFrameworkDsl,
  EmptyBody,
  EmptyBodyInstance,
  FilePart,
  FormOf,
  FreeFormSchema,
  Multipart => BaklavaMultipart,
  Schema,
  TextPart
}
import sttp.model.{Header => SttpHeader, Method => SttpMethod, StatusCode => SttpStatus}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

trait BaklavaHttp4s[TestFrameworkFragmentType, TestFrameworkFragmentsType, TestFrameworkExecutionType[_]]
    extends BaklavaHttpDsl[
      HttpRoutes[IO],
      BaklavaHttp4s.ToEntityMarshaller,
      BaklavaHttp4s.FromEntityUnmarshaller,
      TestFrameworkFragmentType,
      TestFrameworkFragmentsType,
      TestFrameworkExecutionType
    ] {
  this: BaklavaTestFrameworkDsl[
    HttpRoutes[IO],
    BaklavaHttp4s.ToEntityMarshaller,
    BaklavaHttp4s.FromEntityUnmarshaller,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  override type HttpResponse   = Response[IO]
  override type HttpRequest    = Request[IO]
  override type HttpProtocol   = HttpVersion
  override type HttpStatusCode = Status
  override type HttpMethod     = Method
  override type HttpHeaders    = Headers

  override implicit protected def emptyToRequestBodyType: BaklavaHttp4s.ToEntityMarshaller[EmptyBody] =
    EntityEncoder.unitEncoder[IO].contramap(_ => ())

  override implicit protected def formUrlencodedToRequestBodyType[T]: BaklavaHttp4s.ToEntityMarshaller[FormOf[T]] =
    implicitly[EntityEncoder[IO, UrlForm]].contramap { formUrlEncoded =>
      UrlForm(
        formUrlEncoded.fields*
      )
    }

  implicit val urlFormSchema: Schema[UrlForm] = FreeFormSchema("UrlForm")

  /** Multipart marshaller: map each `Baklava.Part` to an `http4s.multipart.Part[IO]` and let http4s's built-in multipart `EntityEncoder`
    * produce the boundary-delimited wire format. `FilePart.contentType` is parsed as a full `Content-Type` header so callers can supply
    * parameters (e.g. `text/plain; charset=UTF-8`) — matching the pekko adapter. Omits `filename` from `Content-Disposition` when the
    * caller left it empty, per the `FilePart` docs.
    */
  override implicit protected def multipartToRequestBodyType: BaklavaHttp4s.ToEntityMarshaller[BaklavaMultipart] =
    implicitly[EntityEncoder[IO, org.http4s.multipart.Multipart[IO]]].contramap { baklavaMultipart =>
      // Annotate the vector type so Scala 2.13 narrows the union of `Part[Pure]` / `Part[IO]`
      // inferred from the match branches down to the `Part[IO]` the Multipart constructor wants.
      val parts: Vector[Http4sPart[IO]] = baklavaMultipart.parts.toVector.map {
        case FilePart(name, contentType, filename, bytes) =>
          val ct = headers.`Content-Type`
            .parse(contentType)
            .toOption
            .getOrElse(headers.`Content-Type`(MediaType.application.`octet-stream`))
          val body = Stream.chunk(fs2.Chunk.array(bytes))
          if (filename.isEmpty)
            Http4sPart[IO](
              Headers(
                headers.`Content-Disposition`("form-data", Map(CIString("name") -> name)),
                (headers.`Content-Transfer-Encoding`.Binary: headers.`Content-Transfer-Encoding`),
                ct
              ),
              body
            )
          else
            Http4sPart.fileData[IO](name, filename, body, ct)
        case TextPart(name, value) =>
          Http4sPart.formData[IO](name, value)
      }
      // Deterministic boundary — http4s's no-arg `Multipart(...)` apply is deprecated because
      // creating a random boundary is an effect; we don't care which boundary is used as long
      // as it's valid, so fix one and skip the side-effecting generator.
      org.http4s.multipart.Multipart[IO](parts, Boundary("baklava-multipart-boundary"))
    }

  override implicit protected def emptyToResponseBodyType: BaklavaHttp4s.FromEntityUnmarshaller[EmptyBody] =
    EntityDecoder.void[IO].map(_ => EmptyBodyInstance)

  override implicit def statusCodeToBaklavaStatusCodes(statusCode: Status): SttpStatus = SttpStatus(statusCode.code)

  override implicit def baklavaStatusCodeToStatusCode(status: SttpStatus): Status = Status
    .fromInt(status.code)
    .getOrElse(throw new IllegalStateException(s"Invalid status code: ${status.code}"))

  override implicit def httpMethodToBaklavaHttpMethod(method: Method): SttpMethod = SttpMethod(method.name)

  override implicit def baklavaHttpMethodToHttpMethod(method: SttpMethod): Method =
    Method.fromString(method.method).getOrElse(throw new IllegalStateException(s"Invalid method: ${method.method}"))

  override implicit def baklavaHttpProtocolToHttpProtocol(baklavaHttpProtocol: BaklavaHttpProtocol): HttpVersion =
    baklavaHttpProtocol.protocol match {
      case "HTTP/0.9" => HttpVersion.`HTTP/0.9`
      case "HTTP/1.0" => HttpVersion.`HTTP/1.0`
      case "HTTP/1.1" => HttpVersion.`HTTP/1.1`
      case "HTTP/2.0" => HttpVersion.`HTTP/2`
      case "HTTP/3"   => HttpVersion.`HTTP/3`
      case _          => throw new IllegalStateException(s"Invalid protocol: ${baklavaHttpProtocol.protocol}")
    }

  override implicit def httpProtocolToBaklavaHttpProtocol(protocol: HttpVersion): BaklavaHttpProtocol = BaklavaHttpProtocol(
    protocol.toString
  )

  override implicit def baklavaHeadersToHttpHeaders(headers: Seq[SttpHeader]): Headers =
    Headers(headers.toList.map(h => Header.Raw(CIString(h.name), h.value)))

  override implicit def httpHeadersToBaklavaHeaders(headers: Headers): Seq[SttpHeader] =
    headers.headers.map(h => SttpHeader(h.name.toString, h.value))

  override def httpResponseToBaklavaResponseContext[T: BaklavaHttp4s.FromEntityUnmarshaller: ClassTag](
      request: Request[IO],
      response: Response[IO]
  ): BaklavaResponseContext[T, Request[IO], Response[IO]] = {
    val requestBytes   = request.body.compile.toVector.unsafeRunSync()
    val requestString  = new String(requestBytes.toArray, "UTF-8")
    val newRequest     = request.withBodyStream(fs2.Stream.emits(requestBytes))
    val responseBytes  = response.body.compile.toVector.unsafeRunSync()
    val responseString = new String(responseBytes.toArray, "UTF-8")
    val newResponse    = response.withBodyStream(fs2.Stream.emits(responseBytes))

    BaklavaResponseContext(
      httpProtocolToBaklavaHttpProtocol(response.httpVersion),
      statusCodeToBaklavaStatusCodes(response.status),
      httpHeadersToBaklavaHeaders(response.headers),
      Try(newResponse.as[T].unsafeRunSync()) match {
        case Success(value)     => value
        case Failure(exception) =>
          throw new BaklavaAssertionException(
            s"Failed to decode response body: ${exception.getMessage}\n" +
              s"Expected: ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}, but got: ${response.status.code} -> ${responseString.take(maxBodyLengthInAssertion)}"
          )
      },
      newRequest,
      requestString,
      newResponse,
      responseString,
      request.headers.get[headers.`Content-Type`].map(ct => s"${ct.mediaType.mainType}/${ct.mediaType.subType}"),
      response.headers.get[headers.`Content-Type`].map(ct => s"${ct.mediaType.mainType}/${ct.mediaType.subType}")
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
      requestBody: BaklavaHttp4s.ToEntityMarshaller[RequestBody]
  ): HttpRequest = {
    // If the test declared a parseable `Content-Type` header, use its value to override the
    // content type that the `EntityEncoder` bakes in. http4s stores Content-Type on the entity
    // (not a free header), so we pull it out of the header list before attaching and then re-set
    // it with `.withContentType` on the resulting request. We only strip after parsing succeeds
    // so an invalid value isn't silently swallowed.
    val parsedOverride = findContentTypeOverride(ctx.headers)
    val otherHeaders   = if (parsedOverride.isDefined) dropContentType(ctx.headers) else ctx.headers
    val base           = Request[IO](
      method = baklavaHttpMethodToHttpMethod(ctx.method.get),
      uri = Uri.fromString(ctx.path).fold(throw _, identity),
      headers = baklavaHeadersToHttpHeaders(otherHeaders)
    )
    val withBody = ctx.body match {
      case Some(body) => base.withEntity(body)
      case None       => base
    }
    parsedOverride.fold(withBody)(ct => withBody.withContentType(ct))
  }

  /** Find a `Content-Type` in the declared headers (case-insensitive) and return the parsed http4s `Content-Type`. Throws on either
    * multiple declarations or an unparseable value — both are always test-authoring bugs.
    */
  private def findContentTypeOverride(hs: Seq[SttpHeader]): Option[headers.`Content-Type`] = {
    val cts = hs.filter(_.name.toLowerCase(java.util.Locale.ROOT) == "content-type")
    cts match {
      case Seq()       => None
      case Seq(single) =>
        headers.`Content-Type`.parse(single.value) match {
          case Right(ct)   => Some(ct)
          case Left(error) =>
            throw new IllegalArgumentException(
              s"Could not parse declared Content-Type header '${single.value}': ${error.message}"
            )
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

  implicit val runtime: IORuntime
}

object BaklavaHttp4s {
  type ToEntityMarshaller[T]     = EntityEncoder[IO, T]
  type FromEntityUnmarshaller[T] = EntityDecoder[IO, T]
}
