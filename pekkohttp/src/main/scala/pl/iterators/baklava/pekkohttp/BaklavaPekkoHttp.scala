package pl.iterators.baklava.pekkohttp

import org.apache.pekko.http.scaladsl.client.RequestBuilding.RequestBuilder
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{ContentType, FormData, HttpEntity, HttpHeader, MessageEntity}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.util.ByteString
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
  FreeFormSchema,
  Schema
}
import sttp.model.{Header => SttpHeader, Method, StatusCode}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

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

  override implicit def statusCodeToBaklavaStatusCodes(statusCode: HttpStatusCode): StatusCode = StatusCode(statusCode.intValue())
  override implicit def baklavaStatusCodeToStatusCode(status: StatusCode): HttpStatusCode      =
    org.apache.pekko.http.scaladsl.model.StatusCode.int2StatusCode(status.code)

  override implicit def httpMethodToBaklavaHttpMethod(method: HttpMethod): Method = Method(method.value)
  override implicit def baklavaHttpMethodToHttpMethod(method: Method): HttpMethod =
    method.method match {
      case "GET"     => org.apache.pekko.http.scaladsl.model.HttpMethods.GET
      case "POST"    => org.apache.pekko.http.scaladsl.model.HttpMethods.POST
      case "PUT"     => org.apache.pekko.http.scaladsl.model.HttpMethods.PUT
      case "DELETE"  => org.apache.pekko.http.scaladsl.model.HttpMethods.DELETE
      case "PATCH"   => org.apache.pekko.http.scaladsl.model.HttpMethods.PATCH
      case "OPTIONS" => org.apache.pekko.http.scaladsl.model.HttpMethods.OPTIONS
      case "HEAD"    => org.apache.pekko.http.scaladsl.model.HttpMethods.HEAD
      case "TRACE"   => org.apache.pekko.http.scaladsl.model.HttpMethods.TRACE
      case "CONNECT" => org.apache.pekko.http.scaladsl.model.HttpMethods.CONNECT
      case other     => org.apache.pekko.http.scaladsl.model.HttpMethod.custom(other)
    }

  override implicit def baklavaHttpProtocolToHttpProtocol(baklavaHttpProtocol: BaklavaHttpProtocol): HttpProtocol =
    baklavaHttpProtocol.protocol match {
      case "HTTP/1.0" => org.apache.pekko.http.scaladsl.model.HttpProtocols.`HTTP/1.0`
      case "HTTP/1.1" => org.apache.pekko.http.scaladsl.model.HttpProtocols.`HTTP/1.1`
      case "HTTP/2"   => org.apache.pekko.http.scaladsl.model.HttpProtocols.`HTTP/2.0`
      case _          => throw new IllegalArgumentException(s"Unsupported protocol: ${baklavaHttpProtocol.protocol}")
    }

  override implicit def httpProtocolToBaklavaHttpProtocol(protocol: HttpProtocol): BaklavaHttpProtocol =
    BaklavaHttpProtocol(protocol.value)

  override implicit def baklavaHeadersToHttpHeaders(headers: Seq[SttpHeader]): HttpHeaders =
    headers
      .map(h => HttpHeader.parse(h.name, h.value))
      .collect { case HttpHeader.ParsingResult.Ok(header, _) => header }

  override implicit def httpHeadersToBaklavaHeaders(headers: HttpHeaders): Seq[SttpHeader] =
    headers.map(h => SttpHeader(h.name(), h.value())).toSeq

  override def httpResponseToBaklavaResponseContext[T: FromEntityUnmarshaller: ClassTag](
      request: HttpRequest,
      response: HttpResponse
  ): BaklavaResponseContext[T, HttpRequest, HttpResponse] = {
    val dataBytes           = Await.result(response.entity.dataBytes.runWith(Sink.fold(ByteString.empty)(_ ++ _)), Duration.Inf)
    val firstResponseEntity = HttpEntity.apply(
      response.entity.contentType,
      dataBytes
    )
    val secondResponseEntity = HttpEntity.apply(
      response.entity.contentType,
      dataBytes
    )

    val responseString = Await.result(implicitly[FromEntityUnmarshaller[String]].apply(secondResponseEntity), Duration.Inf)
    val requestString  = Await.result(implicitly[FromEntityUnmarshaller[String]].apply(request.entity), Duration.Inf)

    BaklavaResponseContext(
      httpProtocolToBaklavaHttpProtocol(response.protocol),
      statusCodeToBaklavaStatusCodes(response.status),
      httpHeadersToBaklavaHeaders(response.headers),
      Try(Await.result(implicitly[FromEntityUnmarshaller[T]].apply(firstResponseEntity), Duration.Inf)) match {
        case Success(value)     => value
        case Failure(exception) =>
          throw new BaklavaAssertionException(
            s"Failed to decode response body: ${exception.getMessage}\n" +
              s"Expected: ${implicitly[ClassTag[T]].runtimeClass.getSimpleName}, but got: ${response.status} -> ${responseString.take(maxBodyLengthInAssertion)}"
          )
      },
      request,
      requestString,
      response,
      responseString,
      Option.when(request.entity.contentType != HttpEntity.Empty.contentType)(
        request.entity.contentType.value
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
    // If the test declared a `Content-Type` header, use its value to override the content type
    // that the implicit marshaller would otherwise bake into the request. This is what lets tests
    // document non-JSON uploads — `h[String]("Content-Type") = "image/png"` + a String/byte body.
    // Pekko-http treats `Content-Type` as part of the entity (not a free header), so we also
    // strip it from the headers list before attaching.
    val (contentTypeOverride, otherHeaders) = splitContentType(ctx.headers)
    val base                                = new RequestBuilder(baklavaHttpMethodToHttpMethod(ctx.method.get))(ctx.path, ctx.body)
      .withHeaders(baklavaHeadersToHttpHeaders(otherHeaders))
    contentTypeOverride.flatMap(v => ContentType.parse(v).toOption) match {
      case Some(ct) => base.withEntity(base.entity.withContentType(ct))
      case None     => base
    }
  }

  private def splitContentType(headers: Seq[SttpHeader]): (Option[String], Seq[SttpHeader]) = {
    val (ct, rest) = headers.partition(_.name.toLowerCase(java.util.Locale.ROOT) == "content-type")
    (ct.headOption.map(_.value), rest)
  }

  override implicit protected def emptyToRequestBodyType: ToEntityMarshaller[EmptyBody] =
    Marshaller.strict[EmptyBody, MessageEntity](_ => Marshalling.Opaque(() => HttpEntity.Empty))

  override implicit protected def formUrlencodedToRequestBodyType[T]: ToEntityMarshaller[FormOf[T]] =
    implicitly[ToEntityMarshaller[FormData]].compose { formUrlencoded =>
      FormData(formUrlencoded.fields*)
    }

  implicit val formDataSchema: Schema[FormData] = FreeFormSchema("FormData")

  override implicit protected def emptyToResponseBodyType: FromEntityUnmarshaller[EmptyBody] =
    Unmarshaller.strict(_ => EmptyBodyInstance)

  implicit val executionContext: ExecutionContext
  implicit val materializer: Materializer
}
