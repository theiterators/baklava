package pl.iterators.baklava.pekkohttp

import org.apache.pekko.http.scaladsl.client.RequestBuilding.RequestBuilder
import org.apache.pekko.http.scaladsl.marshalling.{Marshaller, Marshalling, ToEntityMarshaller}
import org.apache.pekko.http.scaladsl.model.{
  ContentType,
  ContentTypes,
  FormData,
  HttpEntity,
  HttpHeader,
  MessageEntity,
  Multipart => PekkoMultipart
}
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
  FilePart,
  FormOf,
  FreeFormSchema,
  Multipart => BaklavaMultipart,
  Schema,
  TextPart
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
    // document non-JSON uploads — `h[String]("Content-Type") = "image/png"` plus a String/byte
    // body. Pekko-http treats `Content-Type` as part of the entity (not a free header), so we
    // also strip it from the headers list before attaching. Invalid values throw — a silent
    // fallback would mask test-authoring bugs (pekko's own header parser would drop the invalid
    // header from the request, leaving the marshaller's default Content-Type in its place with
    // no indication that the declared value was ignored).
    val parsedOverride = findContentTypeOverride(ctx.headers)
    val otherHeaders   = if (parsedOverride.isDefined) dropContentType(ctx.headers) else ctx.headers
    val base           = new RequestBuilder(baklavaHttpMethodToHttpMethod(ctx.method.get))(ctx.path, ctx.body)
      .withHeaders(baklavaHeadersToHttpHeaders(otherHeaders))
    parsedOverride.fold(base)(ct => base.withEntity(base.entity.withContentType(ct)))
  }

  /** Find a `Content-Type` in the declared headers (case-insensitive) and return the parsed pekko `ContentType`. Throws on either multiple
    * declarations or an unparseable value — both are always test-authoring bugs.
    */
  private def findContentTypeOverride(headers: Seq[SttpHeader]): Option[ContentType] = {
    val cts = headers.filter(_.name.toLowerCase(java.util.Locale.ROOT) == "content-type")
    cts match {
      case Seq()       => None
      case Seq(single) =>
        ContentType.parse(single.value) match {
          case Right(ct)    => Some(ct)
          case Left(errors) =>
            throw new IllegalArgumentException(
              s"Could not parse declared Content-Type header '${single.value}': ${errors.mkString("; ")}"
            )
        }
      case multiple =>
        throw new IllegalArgumentException(
          s"Multiple Content-Type headers declared on one request: [${multiple.map(_.value).mkString(", ")}]. " +
            "Declare a single Content-Type or none at all."
        )
    }
  }

  private def dropContentType(headers: Seq[SttpHeader]): Seq[SttpHeader] =
    headers.filterNot(_.name.toLowerCase(java.util.Locale.ROOT) == "content-type")

  override implicit protected def emptyToRequestBodyType: ToEntityMarshaller[EmptyBody] =
    Marshaller.strict[EmptyBody, MessageEntity](_ => Marshalling.Opaque(() => HttpEntity.Empty))

  override implicit protected def formUrlencodedToRequestBodyType[T]: ToEntityMarshaller[FormOf[T]] =
    implicitly[ToEntityMarshaller[FormData]].compose { formUrlencoded =>
      FormData(formUrlencoded.fields*)
    }

  implicit val formDataSchema: Schema[FormData] = FreeFormSchema("FormData")

  /** Multipart marshaller: each `Part` becomes a pekko-http `BodyPart` with the right Content-Disposition / Content-Type headers, then we
    * serialize with a *fixed* boundary so the captured request body is deterministic across test runs (pekko-http's default marshaller
    * uses a random boundary per call, which would break the gold test).
    */
  override implicit protected def multipartToRequestBodyType: ToEntityMarshaller[BaklavaMultipart] =
    Marshaller.strict[BaklavaMultipart, MessageEntity] { baklavaMultipart =>
      val parts = baklavaMultipart.parts.map {
        case FilePart(name, contentType, filename, bytes) =>
          val ct     = ContentType.parse(contentType).fold(_ => ContentTypes.`application/octet-stream`, identity)
          val entity = HttpEntity(ct, bytes)
          if (filename.nonEmpty) PekkoMultipart.FormData.BodyPart(name, entity, Map("filename" -> filename))
          else PekkoMultipart.FormData.BodyPart(name, entity)
        case TextPart(name, value) =>
          PekkoMultipart.FormData.BodyPart(name, HttpEntity(value))
      }
      Marshalling.Opaque(() => PekkoMultipart.FormData(parts: _*).toEntity(boundary = "baklava-multipart-boundary"))
    }

  override implicit protected def emptyToResponseBodyType: FromEntityUnmarshaller[EmptyBody] =
    Unmarshaller.strict(_ => EmptyBodyInstance)

  implicit val executionContext: ExecutionContext
  implicit val materializer: Materializer
}
