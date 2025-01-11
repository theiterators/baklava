package pl.iterators.baklava

import java.net.URI

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
    securitySchemes: Seq[SecurityScheme],
    body: Option[Body],
    bodySchema: Option[Schema[Body]],
    headers: BaklavaHttpHeaders,
    security: AppliedSecurity,
    pathParameters: PathParameters,
    pathParametersProvided: PathParametersProvided,
    pathParametersSeq: Seq[PathParam[?]],
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

sealed trait Security {
  val `type`: String
  val description: String
  def descriptionParsed: Option[String] = if (description.trim.isEmpty) None else Some(description.trim)
}

case class SecurityScheme(name: String, security: Security)

case class AppliedSecurity(security: Security, params: Map[String, String])

private[baklava] case object NoopSecurity extends Security {
  override val `type`: String      = "noop"
  override val description: String = "this should never be rendered"
}

case class HttpBearer(bearerFormat: String = "", description: String = "") extends Security {
  override val `type`: String = "http"
  val `scheme`: String        = "bearer"

  def apply(token: String): AppliedSecurity = AppliedSecurity(this, Map("token" -> token))
}

case class HttpBasic(description: String = "") extends Security {
  override val `type`: String = "http"
  val `scheme`: String        = "basic"

  def apply(id: String, secret: String): AppliedSecurity = AppliedSecurity(this, Map("id" -> id, "secret" -> secret))
}

// TODO: support other schemes?

case class ApiKeyInHeader(name: String, description: String = "") extends Security {
  override val `type`: String = "apiKey"

  def apply(apiKey: String): AppliedSecurity = AppliedSecurity(this, Map("apiKey" -> apiKey))
}

case class ApiKeyInQuery(name: String, description: String = "") extends Security {
  override val `type`: String = "apiKey"

  def apply(apiKey: String): AppliedSecurity = AppliedSecurity(this, Map("apiKey" -> apiKey))
}

case class ApiKeyInCookie(name: String, description: String = "") extends Security {
  override val `type`: String = "apiKey"

  def apply(apiKey: String): AppliedSecurity = AppliedSecurity(this, Map("apiKey" -> apiKey))
}

case class MutualTls(description: String = "") extends Security {
  override val `type`: String = "mutualTLS"

  def apply(): AppliedSecurity = AppliedSecurity(this, Map.empty)
}

case class OpenIdConnectInBearer(openIdConnectUrl: URI, description: String = "") extends Security {
  override val `type`: String = "openIdConnect"

  def apply(token: String): AppliedSecurity = AppliedSecurity(this, Map("token" -> token))
}

case class OpenIdConnectInCookie(openIdConnectUrl: URI, description: String = "") extends Security {
  override val `type`: String = "openIdConnect"

  def apply(name: String, token: String): AppliedSecurity = AppliedSecurity(this, Map("name" -> name, "token" -> token))
}

case class OAuth2InBearer(flows: OAuthFlows, description: String = "") extends Security {
  override val `type`: String = "oauth2"

  def apply(token: String): AppliedSecurity = AppliedSecurity(this, Map("token" -> token))
}

case class OAuth2InCookie(flows: OAuthFlows, description: String = "") extends Security {
  override val `type`: String = "oauth2"

  def apply(name: String, token: String): AppliedSecurity = AppliedSecurity(this, Map("name" -> name, "token" -> token))
}

// TODO: OpenIdConnect, OAuth2 can provide token in query, customer header, POST-form, etc.

case class OAuthFlows(
    implicitFlow: Option[OAuthImplicitFlow] = None,
    passwordFlow: Option[OAuthPasswordFlow] = None,
    clientCredentialsFlow: Option[OAuthClientCredentialsFlow] = None,
    authorizationCodeFlow: Option[OAuthAuthorizationCodeFlow] = None
)

case class OAuthImplicitFlow(
    authorizationUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

case class OAuthPasswordFlow(
    tokenUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

case class OAuthClientCredentialsFlow(
    tokenUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

case class OAuthAuthorizationCodeFlow(
    authorizationUrl: URI,
    tokenUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

trait BaklavaHttpDsl[
    RouteType,
    ToRequestBodyType[_],
    FromResponseBodyType[_],
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType[_]
] extends BaklavaQueryParams
    with BaklavaPathParams {
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
