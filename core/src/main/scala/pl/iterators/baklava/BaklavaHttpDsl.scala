package pl.iterators.baklava

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed trait EmptyBody

case object EmptyBodyInstance extends EmptyBody

case class BaklavaHttpMethod(value: String)

case class BaklavaHttpProtocol(protocol: String)

case class BaklavaHttpStatus(status: Int)

case class BaklavaHttpHeaders(headers: Map[String, String])

case class BaklavaRequestContext[Body, PathParameters, PathParametersProvided, QueryParameters, QueryParametersProvided](
    symbolicPath: String,
    path: String,
    pathDescription: Option[String],
    pathSummary: Option[String],
    method: Option[BaklavaHttpMethod],
    operationDescription: Option[String],
    operationSummary: Option[String],
    operationId: Option[String],
    operationTags: Seq[String],
    body: Option[Body],
    bodySchema: Option[Schema[Body]],
    headers: BaklavaHttpHeaders,
    security: Option[Security],
    pathParameters: PathParameters,
    pathParametersProvided: PathParametersProvided,
    queryParameters: QueryParameters,
    queryParametersProvided: QueryParametersProvided,
    queryParametersSeq: Seq[QueryParam[?]],
    responseDescription: Option[String]
)

case class BaklavaResponseContext[ResponseBody, RequestType, ResponseType](
    protocol: BaklavaHttpProtocol,
    status: BaklavaHttpStatus,
    headers: BaklavaHttpHeaders,
    body: ResponseBody,
    rawRequest: RequestType,
    requestBodyString: String,
    rawResponse: ResponseType,
    responseBodyString: String,
    requestContentType: Option[String],
    responseContentType: Option[String],
    bodySchema: Option[Schema[ResponseBody]] = None
)

trait Security {
  val `type`: String
  val `scheme`: String
}
case class Bearer(payload: String) extends Security {
  override val `type`: String   = "http"
  override val `scheme`: String = "bearer"
}

case class Basic(id: String, secret: String) extends Security {
  override val `type`: String   = "http"
  override val `scheme`: String = "basic"
}

trait ToPathParam[T] {
  def apply(t: T): String
}

case class PathParam[T](name: String)(implicit val tsm: ToPathParam[T], val schema: Schema[T]) {
  type Underlying = T
}

trait ProvidePathParams[T, U] {
  def apply(
      pathParams: T,
      pathParamsProvided: U,
      uri: String
  ): String
}

trait BaklavaHttpDsl[
    RouteType,
    ToRequestBodyType[_],
    FromResponseBodyType[_],
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType[_]
] extends BaklavaQueryParams {
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
      QueryParametersProvided
  ](
      ctx: BaklavaRequestContext[
        RequestBody,
        PathParameters,
        PathParametersProvided,
        QueryParameters,
        QueryParametersProvided
      ],
      _performRequest: (
          BaklavaRequestContext[
            RequestBody,
            PathParameters,
            PathParametersProvided,
            QueryParameters,
            QueryParametersProvided
          ],
          RouteType
      ) => BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse]
  ) {
    def performRequest(route: RouteType): BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse] = _performRequest(ctx, route)
  }

  def p[T](name: String)(implicit tsm: ToPathParam[T], schema: Schema[T]): PathParam[T] = PathParam[T](name)

  implicit val toPathParamString: ToPathParam[String] = new ToPathParam[String] {
    override def apply(t: String): String = t
  }

  implicit val toPathParamUUID: ToPathParam[java.util.UUID] = new ToPathParam[java.util.UUID] {
    override def apply(t: java.util.UUID): String = t.toString
  }

  implicit val providePathParamsUnit: ProvidePathParams[Unit, Unit] = new ProvidePathParams[Unit, Unit] {
    override def apply(
        pathParams: Unit,
        pathParamsProvided: Unit,
        uri: String
    ): String = uri
  }

  implicit def providePathParamsSingleValue[T]: ProvidePathParams[PathParam[T], T] = new ProvidePathParams[PathParam[T], T] {
    override def apply(
        pathParams: PathParam[T],
        pathParamsProvided: T,
        uri: String
    ): String =
      uri.replace(s"{${pathParams.name}}", URLEncoder.encode(pathParams.tsm(pathParamsProvided), StandardCharsets.UTF_8.toString))
  }

  def testCase[PathParameters, QueryParameters](
      s: BaklavaIntermediateTestCase[PathParameters, QueryParameters]
  ): BaklavaIntermediateTestCase[PathParameters, QueryParameters] = s

  protected def baklavaPerformRequest[
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
      ],
      route: RouteType
  )(implicit
      requestBody: ToRequestBodyType[RequestBody],
      responseBody: FromResponseBodyType[ResponseBody]
  ): BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse] = {
    val request: HttpRequest   = baklavaContextToHttpRequest(ctx)(requestBody)
    val response: HttpResponse = performRequest(route, request)
    httpResponseToBaklavaResponseContext(request, response)
  }

  protected implicit def emptyToRequestBodyType: ToRequestBodyType[EmptyBody]

  protected implicit def emptyToResponseBodyType: FromResponseBodyType[EmptyBody]

  implicit def statusCodeToBaklavaStatusCodes(statusCode: HttpStatusCode): BaklavaHttpStatus
  implicit def baklavaStatusCodeToStatusCode(baklavaHttpStatus: BaklavaHttpStatus): HttpStatusCode

  implicit def httpMethodToBaklavaHttpMethod(method: HttpMethod): BaklavaHttpMethod
  implicit def baklavaHttpMethodToHttpMethod(baklavaHttpMethod: BaklavaHttpMethod): HttpMethod

  implicit def baklavaHttpProtocolToHttpProtocol(baklavaHttpProtocol: BaklavaHttpProtocol): HttpProtocol
  implicit def httpProtocolToBaklavaHttpProtocol(protocol: HttpProtocol): BaklavaHttpProtocol

  implicit def baklavaHeadersToHttpHeaders(headers: BaklavaHttpHeaders): HttpHeaders
  implicit def httpHeadersToBaklavaHeaders(headers: HttpHeaders): BaklavaHttpHeaders

  def httpResponseToBaklavaResponseContext[T: FromResponseBodyType](
      request: HttpRequest,
      response: HttpResponse
  ): BaklavaResponseContext[T, HttpRequest, HttpResponse]

  def baklavaContextToHttpRequest[
      RequestBody,
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
  )(implicit requestBody: ToRequestBodyType[RequestBody]): HttpRequest

  def performRequest(routes: RouteType, request: HttpRequest): HttpResponse
}
