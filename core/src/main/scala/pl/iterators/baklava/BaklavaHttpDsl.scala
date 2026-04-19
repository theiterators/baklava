package pl.iterators.baklava

import sttp.model.{Header => SttpHeader, Method, StatusCode}

import scala.reflect.ClassTag

sealed trait EmptyBody

case object EmptyBodyInstance extends EmptyBody

// Kept as a Baklava-specific wrapper since sttp-model has no HTTP-protocol equivalent.
case class BaklavaHttpProtocol(protocol: String)

case class FormOf[T](fields: (String, String)*)(implicit val schema: Schema[T])

object FormOf {
  implicit def schema[T](implicit schema: Schema[T]): Schema[FormOf[T]] = new Schema[FormOf[T]] {
    val `type`: SchemaType                 = schema.`type`
    val className: String                  = schema.className
    val format: Option[String]             = schema.format
    val properties: Map[String, Schema[?]] = schema.properties
    val items: Option[Schema[?]]           = schema.items
    val `enum`: Option[Set[String]]        = schema.`enum`
    val required: Boolean                  = schema.required
    val additionalProperties: Boolean      = schema.additionalProperties
    val default: Option[FormOf[T]]         = None
    val description: Option[String]        = schema.description
  }
}

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
    headers: Seq[SttpHeader],
    headersDefinition: Headers,
    headersProvided: HeadersProvided,
    headersSeq: Seq[Header[?]],
    security: AppliedSecurity,
    pathParameters: PathParameters,
    pathParametersProvided: PathParametersProvided,
    pathParametersSeq: Seq[PathParam[?]],
    queryParameters: QueryParameters,
    queryParametersProvided: QueryParametersProvided,
    queryParametersSeq: Seq[QueryParam[?]],
    responseDescription: Option[String],
    responseHeaders: Seq[Header[?]]
)

case class BaklavaResponseContext[ResponseBody, RequestType, ResponseType](
    protocol: BaklavaHttpProtocol,
    status: StatusCode,
    headers: Seq[SttpHeader],
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
    with BaklavaHeaders {
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

  protected implicit def formUrlencodedToRequestBodyType[T]: ToRequestBodyType[FormOf[T]]

  protected implicit def multipartToRequestBodyType: ToRequestBodyType[Multipart]

  protected implicit def emptyToResponseBodyType: FromResponseBodyType[EmptyBody]

  implicit def statusCodeToBaklavaStatusCodes(statusCode: HttpStatusCode): StatusCode
  implicit def baklavaStatusCodeToStatusCode(status: StatusCode): HttpStatusCode

  implicit def httpMethodToBaklavaHttpMethod(method: HttpMethod): Method
  implicit def baklavaHttpMethodToHttpMethod(method: Method): HttpMethod

  implicit def baklavaHttpProtocolToHttpProtocol(baklavaHttpProtocol: BaklavaHttpProtocol): HttpProtocol
  implicit def httpProtocolToBaklavaHttpProtocol(protocol: HttpProtocol): BaklavaHttpProtocol

  implicit def baklavaHeadersToHttpHeaders(headers: Seq[SttpHeader]): HttpHeaders
  implicit def httpHeadersToBaklavaHeaders(headers: HttpHeaders): Seq[SttpHeader]

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
