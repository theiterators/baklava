package pl.iterators.baklava.pekkohttp

import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{HttpEntity, HttpHeader, MessageEntity}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import org.apache.pekko.stream.Materializer
import pl.iterators.baklava.{
  Baklava2ResponseContext,
  BaklavaEmptyBody,
  BaklavaHttpDsl,
  BaklavaHttpHeaders,
  BaklavaHttpMethod,
  BaklavaHttpProtocol,
  BaklavaHttpStatus,
  BaklavaTestFrameworkDsl
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
    org.apache.pekko.http.scaladsl.model.HttpProtocol(
      baklavaHttpProtocol.protocol
    )
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

  override implicit def httpResponseToBaklavaResponseContext[T: FromEntityUnmarshaller](
      response: HttpResponse
  ): Baklava2ResponseContext[T] = {
    Baklava2ResponseContext(
      response.protocol,
      response.status,
      response.headers,
      Await.result(implicitly[FromEntityUnmarshaller[T]].apply(response.entity), Duration.Inf)
    )
  }

  override val emptyToResponseBodyType: ToEntityMarshaller[BaklavaEmptyBody.type] =
    Marshaller.strict[BaklavaEmptyBody.type, MessageEntity](_ => Marshalling.Opaque(() => HttpEntity.Empty))

  implicit val executionContext: ExecutionContext
  implicit val materializer: Materializer
}
