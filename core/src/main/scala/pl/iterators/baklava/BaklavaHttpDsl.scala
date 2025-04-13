package pl.iterators.baklava

import scala.reflect.ClassTag
import sttp.model._

sealed trait EmptyBody

case object EmptyBodyInstance extends EmptyBody

case class BaklavaRequestContext[
    Body,
    PathParameters,
    PathParametersProvided,
    QueryParameters,
    QueryParametersProvided,
    Headers,
    HeadersProvided
](
    symbolicPath: String,
    path: String,
    pathDescription: Option[String],
    pathSummary: Option[String],
    method: Option[Method],
    operationDescription: Option[String],
    operationSummary: Option[String],
    operationId: Option[String],
    operationTags: Seq[String],
    securitySchemes: Seq[SecurityScheme],
    body: Option[Body],
    bodySchema: Option[Schema[Body]],
    headers: Headers,
    headersDefinition: Headers,
    headersProvided: HeadersProvided,
    headersSeq: Seq[Header],
    security: AppliedSecurity,
    pathParameters: PathParameters,
    pathParametersProvided: PathParametersProvided,
    pathParametersSeq: Seq[PathParam[?]],
    queryParameters: QueryParameters,
    queryParametersProvided: QueryParametersProvided,
    queryParametersSeq: Seq[QueryParam[?]],
    responseDescription: Option[String],
    responseHeaders: Seq[Header]
)

case class BaklavaResponseContext[ResponseBody, RequestType, ResponseType](
    protocol: HttpVersion,
    status: StatusCode,
    headers: Headers,
    body: ResponseBody,
    rawRequest: RequestType,
    requestBodyString: String,
    rawResponse: ResponseType,
    responseBodyString: String,
    requestContentType: Option[String],
    responseContentType: Option[String],
    bodySchema: Option[Schema[ResponseBody]] = None
)

trait BaklavaHttpDsl[
    RouteType,
    ToRequestBodyType[_],
    FromResponseBodyType[_],
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType[_]
] extends BaklavaQueryParams
    with BaklavaPathParams
    with HasHeaders {
  this: BaklavaTestFrameworkDsl[
    RouteType,
    ToRequestBodyType,
    FromResponseBodyType,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  type HttpResponse
  type HttpRequest
  type HttpProtocol
  type HttpStatusCode
  type HttpMethod
  type HttpHeaders

  case class BaklavaCaseContext[
      RequestBody,
      ResponseBody,
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
      ],
      _performRequest: (
          BaklavaRequestContext[
            RequestBody,
            PathParameters,
            PathParametersProvided,
            QueryParameters,
            QueryParametersProvided,
            Headers,
            HeadersProvided
          ],
          RouteType
      ) => BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse]
  ) {
    def performRequest(route: RouteType): BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse] = _performRequest(ctx, route)
  }

  def testCase[PathParameters, QueryParameters, Headers](
      s: BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers]
  ): BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers] = s

  protected def baklavaPerformRequest[
      RequestBody,
      ResponseBody,
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
      ],
      route: RouteType
  )(implicit
      requestBody: ToRequestBodyType[RequestBody],
      responseBody: FromResponseBodyType[ResponseBody],
      classTag: ClassTag[ResponseBody]
  ): BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse] = {
    val request: HttpRequest   = baklavaContextToHttpRequest(ctx)(requestBody)
    val response: HttpResponse = performRequest(route, request)
    httpResponseToBaklavaResponseContext(request, response)
  }

  protected implicit def emptyToRequestBodyType: ToRequestBodyType[EmptyBody]

  protected implicit def emptyToResponseBodyType: FromResponseBodyType[EmptyBody]

  implicit def statusCodeToBaklavaStatusCodes(statusCode: HttpStatusCode): StatusCode
  implicit def baklavaStatusCodeToStatusCode(baklavaHttpStatus: StatusCode): HttpStatusCode

  implicit def httpMethodToBaklavaHttpMethod(method: HttpMethod): Method
  implicit def baklavaHttpMethodToHttpMethod(baklavaHttpMethod: Method): HttpMethod

  implicit def baklavaHttpProtocolToHttpProtocol(baklavaHttpProtocol: HttpVersion): HttpProtocol
  implicit def httpProtocolToBaklavaHttpProtocol(protocol: HttpProtocol): HttpVersion

  implicit def baklavaHeadersToHttpHeaders(headers: Headers): HttpHeaders
  implicit def httpHeadersToBaklavaHeaders(headers: HttpHeaders): Headers

  def httpResponseToBaklavaResponseContext[T: FromResponseBodyType: ClassTag](
      request: HttpRequest,
      response: HttpResponse
  ): BaklavaResponseContext[T, HttpRequest, HttpResponse]

  def baklavaContextToHttpRequest[
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
  )(implicit requestBody: ToRequestBodyType[RequestBody]): HttpRequest

  def performRequest(routes: RouteType, request: HttpRequest): HttpResponse
}
