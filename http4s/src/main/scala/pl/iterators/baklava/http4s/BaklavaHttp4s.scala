package pl.iterators.baklava.http4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{
  EntityDecoder,
  EntityEncoder,
  Header,
  Headers,
  HttpRoutes,
  HttpVersion,
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
  BaklavaHttpHeaders,
  BaklavaHttpMethod,
  BaklavaHttpProtocol,
  BaklavaHttpStatus,
  BaklavaRequestContext,
  BaklavaResponseContext,
  BaklavaTestFrameworkDsl,
  EmptyBody,
  EmptyBodyInstance,
  FormOf,
  FreeFormSchema,
  Schema
}

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

  override implicit protected def emptyToResponseBodyType: BaklavaHttp4s.FromEntityUnmarshaller[EmptyBody] =
    EntityDecoder.void[IO].map(_ => EmptyBodyInstance)

  override implicit def statusCodeToBaklavaStatusCodes(statusCode: Status): BaklavaHttpStatus = BaklavaHttpStatus(statusCode.code)

  override implicit def baklavaStatusCodeToStatusCode(baklavaHttpStatus: BaklavaHttpStatus): Status = Status
    .fromInt(baklavaHttpStatus.status)
    .getOrElse(throw new IllegalStateException(s"Invalid status code: ${baklavaHttpStatus.status}"))

  override implicit def httpMethodToBaklavaHttpMethod(method: Method): BaklavaHttpMethod = BaklavaHttpMethod(method.name)

  override implicit def baklavaHttpMethodToHttpMethod(baklavaHttpMethod: BaklavaHttpMethod): Method =
    Method.fromString(baklavaHttpMethod.value).getOrElse(throw new IllegalStateException(s"Invalid method: ${baklavaHttpMethod.value}"))

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

  override implicit def baklavaHeadersToHttpHeaders(headers: BaklavaHttpHeaders): Headers = Headers(headers.headers.map { case (k, v) =>
    Header.Raw(CIString(k), v)
  }.toList)

  override implicit def httpHeadersToBaklavaHeaders(headers: Headers): BaklavaHttpHeaders = BaklavaHttpHeaders(
    headers.headers.map(h => h.name.toString -> h.value).toMap
  )

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
      response.httpVersion,
      response.status,
      response.headers,
      Try(newResponse.as[T].unsafeRunSync()) match {
        case Success(value) => value
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
    ctx.body match {
      case Some(body) =>
        Request[IO](
          method = ctx.method.get,
          uri = Uri.fromString(ctx.path).fold(throw _, identity),
          headers = ctx.headers
        ).withEntity(body)
      case None =>
        Request[IO](
          method = ctx.method.get,
          uri = Uri.fromString(ctx.path).fold(throw _, identity),
          headers = ctx.headers
        )
    }
  }

  implicit val runtime: IORuntime
}

object BaklavaHttp4s {
  type ToEntityMarshaller[T]     = EntityEncoder[IO, T]
  type FromEntityUnmarshaller[T] = EntityDecoder[IO, T]
}
