package pl.iterators.baklava.pekkohttp

import org.apache.pekko.http.scaladsl.client.RequestBuilding.RequestBuilder
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpHeader, MessageEntity}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
import pl.iterators.baklava.{
  BaklavaHttpDsl,
  BaklavaHttpHeaders,
  BaklavaHttpMethod,
  BaklavaHttpProtocol,
  BaklavaHttpStatus,
  BaklavaRequestContext,
  BaklavaResponseContext,
  BaklavaTestFrameworkDsl,
  EmptyBody,
  EmptyBodyInstance
}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

trait BaklavaPekkoHttp[TestFrameworkFragmentType, TestFrameworkFragmentsType, TestFrameworkExecutionType[_]]
    extends BaklavaHttpDsl[
      Route,
      ToEntityMarshaller,
      FromEntityUnmarshaller,
      TestFrameworkFragmentType,
      TestFrameworkFragmentsType,
      TestFrameworkExecutionType
    ] {
  this: BaklavaTestFrameworkDsl[
    Route,
    ToEntityMarshaller,
    FromEntityUnmarshaller,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  override type HttpProtocol   = org.apache.pekko.http.scaladsl.model.HttpProtocol
  override type HttpStatusCode = org.apache.pekko.http.scaladsl.model.StatusCode
  override type HttpMethod     = org.apache.pekko.http.scaladsl.model.HttpMethod
  override type HttpHeaders    = Seq[HttpHeader]
  override type HttpRequest    = org.apache.pekko.http.scaladsl.model.HttpRequest
  override type HttpResponse   = org.apache.pekko.http.scaladsl.model.HttpResponse

  override implicit def statusCodeToBaklavaStatusCodes(statusCode: HttpStatusCode): BaklavaHttpStatus = BaklavaHttpStatus(
    statusCode.intValue()
  )
  override implicit def baklavaStatusCodeToStatusCode(baklavaHttpStatus: BaklavaHttpStatus): HttpStatusCode =
    org.apache.pekko.http.scaladsl.model.StatusCode.int2StatusCode(
      baklavaHttpStatus.status
    )

  override implicit def httpMethodToBaklavaHttpMethod(method: HttpMethod): BaklavaHttpMethod = BaklavaHttpMethod(
    method.value
  )
  override implicit def baklavaHttpMethodToHttpMethod(baklavaHttpMethod: BaklavaHttpMethod): HttpMethod =
    baklavaHttpMethod.value match {
      case "GET"     => org.apache.pekko.http.scaladsl.model.HttpMethods.GET
      case "POST"    => org.apache.pekko.http.scaladsl.model.HttpMethods.POST
      case "PUT"     => org.apache.pekko.http.scaladsl.model.HttpMethods.PUT
      case "DELETE"  => org.apache.pekko.http.scaladsl.model.HttpMethods.DELETE
      case "PATCH"   => org.apache.pekko.http.scaladsl.model.HttpMethods.PATCH
      case "OPTIONS" => org.apache.pekko.http.scaladsl.model.HttpMethods.OPTIONS
      case "HEAD"    => org.apache.pekko.http.scaladsl.model.HttpMethods.HEAD
      case "TRACE"   => org.apache.pekko.http.scaladsl.model.HttpMethods.TRACE
      case "CONNECT" => org.apache.pekko.http.scaladsl.model.HttpMethods.CONNECT
      case _         => org.apache.pekko.http.scaladsl.model.HttpMethod.custom(baklavaHttpMethod.value)
    }

  override implicit def baklavaHttpProtocolToHttpProtocol(baklavaHttpProtocol: BaklavaHttpProtocol): HttpProtocol =
    baklavaHttpProtocol.protocol match {
      case "HTTP/1.0" => org.apache.pekko.http.scaladsl.model.HttpProtocols.`HTTP/1.0`
      case "HTTP/1.1" => org.apache.pekko.http.scaladsl.model.HttpProtocols.`HTTP/1.1`
      case "HTTP/2"   => org.apache.pekko.http.scaladsl.model.HttpProtocols.`HTTP/2.0`
      case _          => throw new IllegalArgumentException(s"Unsupported protocol: ${baklavaHttpProtocol.protocol}")
    }

  override implicit def httpProtocolToBaklavaHttpProtocol(protocol: HttpProtocol): BaklavaHttpProtocol = BaklavaHttpProtocol(
    protocol.value
  )

  override implicit def baklavaHeadersToHttpHeaders(headers: BaklavaHttpHeaders): HttpHeaders = headers.headers
    .map { case (k, v) =>
      HttpHeader.parse(k, v)
    }
    .toSeq
    .collect { case HttpHeader.ParsingResult.Ok(header, _) =>
      header
    }
  override implicit def httpHeadersToBaklavaHeaders(headers: HttpHeaders): BaklavaHttpHeaders = BaklavaHttpHeaders(
    headers.map(h => h.name() -> h.value()).toMap
  )

  override def httpResponseToBaklavaResponseContext[T: FromEntityUnmarshaller](
      request: HttpRequest,
      response: HttpResponse
  ): BaklavaResponseContext[T, HttpRequest, HttpResponse] = {
    val dataBytes = Await.result(response.entity.dataBytes.runWith(Sink.fold(ByteString.empty)(_ ++ _)), Duration.Inf)
    val firstResponseEntity = HttpEntity.apply(
      response.entity.contentType,
      dataBytes
    )
    val secondResponseEntity = HttpEntity.apply(
      response.entity.contentType,
      dataBytes
    )

    BaklavaResponseContext(
      response.protocol,
      response.status,
      response.headers,
      Await.result(implicitly[FromEntityUnmarshaller[T]].apply(firstResponseEntity), Duration.Inf),
      request,
      Await.result(implicitly[FromEntityUnmarshaller[String]].apply(request.entity), Duration.Inf),
      response,
      Await.result(implicitly[FromEntityUnmarshaller[String]].apply(secondResponseEntity), Duration.Inf),
      Option.when(request.entity.contentType != HttpEntity.Empty.contentType)(
        response.entity.contentType.value
      ),
      Option.when(response.entity.contentType != HttpEntity.Empty.contentType)(
        response.entity.contentType.value
      )
    )
  }

  override def baklavaContextToHttpRequest[
      RequestBody,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided,
      Headers,
      HeadersProvided
  ](
      ctx: BaklavaRequestContext[
        RequestBody,
        PathParameters,
        PathParametersProvided,
        QueryParameters,
        QueryParametersProvided,
        Headers,
        HeadersProvided
      ]
  )(implicit
      requestBody: ToEntityMarshaller[RequestBody]
  ): HttpRequest = {
    new RequestBuilder(ctx.method.get)(ctx.path, ctx.body)
      .withHeaders(ctx.headers)
  }

  override implicit protected def emptyToRequestBodyType: ToEntityMarshaller[EmptyBody] =
    Marshaller.strict[EmptyBody, MessageEntity](_ => Marshalling.Opaque(() => HttpEntity.Empty))

  override implicit protected def emptyToResponseBodyType: FromEntityUnmarshaller[EmptyBody] =
    Unmarshaller.strict(_ => EmptyBodyInstance)

  implicit val executionContext: ExecutionContext
  implicit val materializer: Materializer
}
