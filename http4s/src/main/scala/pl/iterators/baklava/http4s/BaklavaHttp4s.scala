package pl.iterators.baklava.http4s

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.http4s.{Entity, EntityDecoder, EntityEncoder, Header, Headers, HttpRoutes, HttpVersion, Method, Request, Response, Status, Uri}
import org.typelevel.ci.CIString
import pl.iterators.baklava.{
  BaklavaRequestContext,
  BaklavaResponseContext,
  BaklavaHttpDsl,
  BaklavaHttpHeaders,
  BaklavaHttpMethod,
  BaklavaHttpProtocol,
  BaklavaHttpStatus,
  BaklavaTestFrameworkDsl,
  EmptyBody,
  EmptyBodyInstance
}

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

  override def httpResponseToBaklavaResponseContext[T: BaklavaHttp4s.FromEntityUnmarshaller](
      response: Response[IO]
  ): BaklavaResponseContext[T, Response[IO]] = BaklavaResponseContext(
    response.httpVersion,
    response.status,
    response.headers,
    response.as[T].unsafeRunSync(),
    response
  )

  override def baklavaContextToHttpRequest[
      RequestBody,
      ResponseBody,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided
  ](
      ctx: BaklavaRequestContext[
        RequestBody,
        PathParameters,
        PathParametersProvided,
        QueryParameters,
        QueryParametersProvided
      ]
  )(implicit
      requestBody: BaklavaHttp4s.ToEntityMarshaller[RequestBody],
      responseBody: BaklavaHttp4s.FromEntityUnmarshaller[ResponseBody]
  ): HttpRequest = {
    val entityIO =
      ctx.body.fold(Entity.empty: Entity[IO])(implicitly[BaklavaHttp4s.ToEntityMarshaller[RequestBody]].toEntity)

    Request[IO](
      method = ctx.method.get,
      uri = Uri.fromString(ctx.path).fold(throw _, identity),
      body = entityIO.body,
      headers = ctx.headers
    )
  }

  implicit val runtime: IORuntime
}

object BaklavaHttp4s {
  type ToEntityMarshaller[T]     = EntityEncoder[IO, T]
  type FromEntityUnmarshaller[T] = EntityDecoder[IO, T]
}
