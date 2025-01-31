package pl.iterators.baklava

import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

trait BaklavaTestFrameworkDsl[RouteType, ToRequestBodyType[_], FromResponseBodyType[
    _
], TestFrameworkFragmentType, TestFrameworkFragmentsType, TestFrameworkExecutionType[_]] {
  this: BaklavaHttpDsl[
    RouteType,
    ToRequestBodyType,
    FromResponseBodyType,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  def path(path: String, description: String = "", summary: String = "")(steps: BaklavaMethodDefinition*): TestFrameworkFragmentsType = {
    val ctx: BaklavaRequestContext[Nothing, Any, Any, Any, Any, Any, Any] = BaklavaRequestContext(
      symbolicPath = path,
      path = path,
      pathDescription = if (description.trim.isEmpty) None else Some(description.trim),
      pathSummary = if (summary.trim.isEmpty) None else Some(summary.trim),
      method = None,
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
      securitySchemes = Seq.empty,
      body = None,
      bodySchema = None,
      headers = BaklavaHttpHeaders(Map.empty),
      headersDefinition = (),
      headersProvided = (),
      headersSeq = Seq.empty,
      security = AppliedSecurity(NoopSecurity, Map.empty),
      pathParameters = (),
      pathParametersProvided = (),
      pathParametersSeq = Seq.empty,
      queryParameters = (),
      queryParametersProvided = (),
      queryParametersSeq = Seq.empty,
      responseDescription = None,
      responseHeaders = Seq.empty
    )
    pathLevelTextWithFragments(
      s"$path should",
      ctx,
      concatFragments(
        steps
          .map(
            _.apply(
              ctx
            )
          )
      )
    )
  }

  def supports[PathParameters, QueryParameters, Headers](
      method: BaklavaHttpMethod,
      securitySchemes: Seq[SecurityScheme] = Seq.empty,
      pathParameters: PathParameters = (),
      queryParameters: QueryParameters = (),
      headers: Headers = (),
      description: String = "",
      summary: String = "",
      operationId: String = "",
      tags: Seq[String] = Seq.empty
  )(
      steps: BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers]*
  )(implicit
      toQueryParamSeq: ToQueryParamSeq[QueryParameters],
      toPathParamSeq: ToPathParamSeq[PathParameters],
      toHeaderSeq: ToHeaderSeq[Headers]
  ): BaklavaMethodDefinition =
    new BaklavaMethodDefinition {
      override def apply(ctx: BaklavaRequestContext[Nothing, Any, Any, Any, Any, Any, Any]): TestFrameworkFragmentsType = {
        val finalSummary = if (summary.trim.isEmpty) "" else ": " + summary.trim
        val newCtx = BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit, Headers, Unit](
          symbolicPath = ctx.symbolicPath,
          path = ctx.path,
          pathDescription = ctx.pathDescription,
          pathSummary = ctx.pathSummary,
          method = Some(method),
          securitySchemes = securitySchemes,
          operationDescription = if (description.trim.isEmpty) None else Some(description.trim),
          operationSummary = if (summary.trim.isEmpty) None else Some(summary.trim),
          operationId = if (operationId.trim.isEmpty) None else Some(operationId.trim),
          operationTags = tags,
          body = None,
          bodySchema = None,
          headers = ctx.headers,
          headersDefinition = headers,
          headersProvided = (),
          headersSeq = toHeaderSeq.apply(headers),
          security = AppliedSecurity(NoopSecurity, Map.empty),
          pathParameters = pathParameters,
          pathParametersProvided = (),
          pathParametersSeq = toPathParamSeq.apply(pathParameters),
          queryParameters = queryParameters,
          queryParametersProvided = (),
          queryParametersSeq = toQueryParamSeq.apply(queryParameters),
          responseDescription = None,
          responseHeaders = Seq.empty
        )
        methodLevelTextWithFragments(s"support ${method.value}" + finalSummary, newCtx, fragmentsFromSeq(steps.map(_.apply(newCtx))))
      }
    }

  trait BaklavaMethodDefinition {
    def apply(ctx: BaklavaRequestContext[Nothing, Any, Any, Any, Any, Any, Any]): TestFrameworkFragmentsType
  }

  trait BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers] {
    def apply(ctx: BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit, Headers, Unit]): TestFrameworkFragmentType
  }

  trait BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] {
    def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters, Headers](
        r: BaklavaCaseContext[
          RequestBody,
          ResponseBody,
          PathParameters,
          PathParametersProvided,
          QueryParameters,
          QueryParametersProvided,
          Headers,
          HeadersProvided
        ] => R
    )(implicit
        providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
        provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided],
        provideHeaders: ProvideHeaders[Headers, HeadersProvided]
    ): BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers]
  }

  def strictHeaderCheckDefault: Boolean

  case class OnRequest[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
      body: RequestBody,
      security: AppliedSecurity,
      headersProvided: HeadersProvided,
      pathParametersProvided: PathParametersProvided,
      queryParametersProvided: QueryParametersProvided
  ) {
    def respondsWith[ResponseBody: FromResponseBodyType: Schema](
        statusCode: BaklavaHttpStatus,
        headers: Seq[Header[?]] = Seq.empty,
        description: String = "",
        strictHeaderCheck: Boolean = strictHeaderCheckDefault
    ): BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
      new BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] {
        override def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters, Headers](
            r: BaklavaCaseContext[
              RequestBody,
              ResponseBody,
              PathParameters,
              PathParametersProvided,
              QueryParameters,
              QueryParametersProvided,
              Headers,
              HeadersProvided
            ] => R
        )(implicit
            providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
            provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided],
            provideHeaders: ProvideHeaders[Headers, HeadersProvided]
        ): BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers] =
          new BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers] {
            override def apply(
                requestContext: BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit, Headers, Unit]
            ): TestFrameworkFragmentType = {
              val finalDescription = if (description.trim.isEmpty) "" else ": " + description.trim
              var timesCalled: Int = 0

              val headersToInclude = provideHeaders.apply(requestContext.headersDefinition, headersProvided)
              // TODO: this security logic is duplicated in multiple places and messy and NEEDS TESTS
              // TODO: e.g. cookie concatenation by hand?!
              val additionalSecurityHeaders = security match {
                case AppliedSecurity(_: HttpBearer, params) => Map("Authorization" -> s"Bearer ${params("token")}")
                case AppliedSecurity(_: HttpBasic, params) =>
                  Map("Authorization" -> s"Basic ${Base64.getEncoder.encodeToString(s"${params("id")}:${params("secret")}".getBytes)}")
                case AppliedSecurity(s: ApiKeyInHeader, params)        => Map(s.name -> params("apiKey"))
                case AppliedSecurity(_: ApiKeyInQuery, _)              => Map.empty[String, String]
                case AppliedSecurity(_: ApiKeyInCookie, _)             => Map.empty[String, String]
                case AppliedSecurity(_: MutualTls, _)                  => Map.empty[String, String]
                case AppliedSecurity(_: OpenIdConnectInBearer, params) => Map("Authorization" -> s"Bearer ${params("token")}")
                case AppliedSecurity(_: OpenIdConnectInCookie, _)      => Map.empty[String, String]
                case AppliedSecurity(_: OAuth2InBearer, params)        => Map("Authorization" -> s"Bearer ${params("token")}")
                case AppliedSecurity(_: OAuth2InCookie, _)             => Map.empty[String, String]
                case AppliedSecurity(_: NoopSecurity.type, _)          => Map.empty[String, String]
              }
              val headersWithCookieModifiedForSecurity: Map[String, String] = security match {
                case AppliedSecurity(_: HttpBearer, _)     => headersToInclude
                case AppliedSecurity(_: HttpBasic, _)      => headersToInclude
                case AppliedSecurity(_: ApiKeyInHeader, _) => headersToInclude
                case AppliedSecurity(_: ApiKeyInQuery, _)  => headersToInclude
                case AppliedSecurity(s: ApiKeyInCookie, params) =>
                  headersToInclude.find(_._1.toLowerCase == "cookie") match {
                    case Some((_, value)) => headersToInclude + ("Cookie" -> s"$value; ${s.name}=${params("apiKey")}")
                    case None             => headersToInclude + ("Cookie" -> s"${s.name}=${params("apiKey")}")
                  }
                case AppliedSecurity(_: MutualTls, _)             => headersToInclude
                case AppliedSecurity(_: OpenIdConnectInBearer, _) => headersToInclude
                case AppliedSecurity(_: OpenIdConnectInCookie, params) =>
                  headersToInclude.find(_._1.toLowerCase == "cookie") match {
                    case Some((_, value)) => headersToInclude + ("Cookie" -> s"$value; ${params("name")}=${params("token")}")
                    case None             => headersToInclude + ("Cookie" -> s"${params("name")}=${params("token")}")
                  }
                case AppliedSecurity(_: OAuth2InBearer, _) => headersToInclude
                case AppliedSecurity(_: OAuth2InCookie, params) =>
                  headersToInclude.find(_._1.toLowerCase == "cookie") match {
                    case Some((_, value)) => headersToInclude + ("Cookie" -> s"$value; ${params("name")}=${params("token")}")
                    case None             => headersToInclude + ("Cookie" -> s"${params("name")}=${params("token")}")
                  }
                case AppliedSecurity(_: NoopSecurity.type, _) => headersToInclude
              }
              val securityQueryParameters = security match {
                case AppliedSecurity(_: HttpBearer, _)            => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: HttpBasic, _)             => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: ApiKeyInHeader, _)        => Map.empty[String, Seq[String]]
                case AppliedSecurity(s: ApiKeyInQuery, params)    => Map(s.name -> Seq(params("apiKey")))
                case AppliedSecurity(_: ApiKeyInCookie, _)        => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: MutualTls, _)             => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: OpenIdConnectInBearer, _) => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: OpenIdConnectInCookie, _) => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: OAuth2InBearer, _)        => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: OAuth2InCookie, _)        => Map.empty[String, Seq[String]]
                case AppliedSecurity(_: NoopSecurity.type, _)     => Map.empty[String, Seq[String]]
              }

              val finalRequestCtx =
                requestContext.copy(
                  path = provideQueryParams.apply(
                    requestContext.queryParameters,
                    queryParametersProvided,
                    providePathParams.apply(
                      requestContext.pathParameters,
                      pathParametersProvided,
                      addQueryParametersToUri(requestContext.path, securityQueryParameters)
                    )
                  ),
                  body = if (body != EmptyBodyInstance) Some(body) else None,
                  bodySchema = Some(implicitly[Schema[RequestBody]]),
                  headers = BaklavaHttpHeaders(headersWithCookieModifiedForSecurity ++ additionalSecurityHeaders),
                  headersProvided = headersProvided,
                  security = security,
                  pathParametersProvided = pathParametersProvided,
                  queryParametersProvided = queryParametersProvided,
                  responseDescription = if (description.trim.isEmpty) None else Some(description.trim),
                  responseHeaders = headers
                )

              requestLevelTextWithExecution(
                statusCode.status.toString + finalDescription,
                finalRequestCtx, {
                  val wrappedPerformRequest = (
                      requestContext: BaklavaRequestContext[
                        RequestBody,
                        PathParameters,
                        PathParametersProvided,
                        QueryParameters,
                        QueryParametersProvided,
                        Headers,
                        HeadersProvided
                      ],
                      route: RouteType
                  ) => {
                    val responseContext =
                      baklavaPerformRequest[
                        RequestBody,
                        ResponseBody,
                        PathParameters,
                        PathParametersProvided,
                        QueryParameters,
                        QueryParametersProvided,
                        Headers,
                        HeadersProvided
                      ](
                        requestContext,
                        route
                      )
                    timesCalled += 1

                    if (responseContext.status != statusCode) {
                      throw new RuntimeException(
                        s"Expected status code ${statusCode.status}, but got ${responseContext.status.status}"
                      )
                    }

                    if (responseContext.responseBodyString.nonEmpty && implicitly[Schema[ResponseBody]] == Schema.emptyBodySchema) {
                      throw new RuntimeException("Expected empty response body, but got: " + responseContext.responseBodyString)
                    }

                    val headersParsed = headers.map { h =>
                      responseContext.headers.headers.get(h.name) match { // TODO: should be case insensitive
                        case None => throw new RuntimeException(s"Header ${h.name} not found but expected")
                        case Some(value) =>
                          h.name ->
                          h.tsm
                            .unapply(value)
                            .getOrElse(
                              throw new RuntimeException(
                                s"Header ${h.name} with value $value could not be parsed as ${h.schema.className}"
                              )
                            )
                      }
                    }
                    if (strictHeaderCheck && headersParsed.distinctBy(_._1).length != responseContext.headers.headers.size) {
                      throw new RuntimeException(
                        s"Strict headers check is on, expected following headers: [${headers
                            .map(h => h.name)
                            .sorted
                            .mkString(", ")}], but got: [${responseContext.headers.headers.keys.toList.sorted.mkString(", ")}]"
                      )
                    }

                    if (
                      requestContext.security.security != NoopSecurity && !requestContext.securitySchemes
                        .exists(_.security == requestContext.security.security)
                    ) {
                      throw new RuntimeException(
                        s"Used security ${requestContext.security.security.`type`} is not defined in security schemes: [${requestContext.securitySchemes.map(ss => s"${ss.name} -> ${ss.security.`type`}").mkString(", ")}]"
                      )
                    }

                    updateStorage(requestContext, responseContext.copy(bodySchema = Some(implicitly[Schema[ResponseBody]])))
                    responseContext
                  }
                  val baklava2CaseContext = BaklavaCaseContext(finalRequestCtx, wrappedPerformRequest)
                  r.andThen { x =>
                    if (timesCalled == 0) {
                      throw new RuntimeException("oi, mate! performRequest was not called")
                    } else if (timesCalled > 1) {
                      throw new RuntimeException("oi, mate! performRequest was called more than once")
                    }
                    x
                  }(baklava2CaseContext)
                }
              )
            }
          }
      }
  }

  def onRequest: OnRequest[EmptyBody, Unit, Unit, Unit] =
    onRequest(EmptyBodyInstance: EmptyBody, AppliedSecurity(NoopSecurity, Map.empty), (), (), ())(
      emptyToRequestBodyType,
      Schema.emptyBodySchema
    )
  def onRequest[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
      body: RequestBody = EmptyBodyInstance: EmptyBody,
      security: AppliedSecurity = AppliedSecurity(NoopSecurity, Map.empty),
      pathParameters: PathParametersProvided = (),
      queryParameters: QueryParametersProvided = (),
      headers: HeadersProvided = ()
  ): OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
    OnRequest(body, security, headers, pathParameters, queryParameters)

  def fragmentsFromSeq(fragments: Seq[TestFrameworkFragmentType]): TestFrameworkFragmentsType
  def concatFragments(fragments: Seq[TestFrameworkFragmentsType]): TestFrameworkFragmentsType
  def pathLevelTextWithFragments(
      text: String,
      context: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?],
      fragments: => TestFrameworkFragmentsType
  ): TestFrameworkFragmentsType
  def methodLevelTextWithFragments(
      text: String,
      context: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?],
      fragments: => TestFrameworkFragmentsType
  ): TestFrameworkFragmentsType
  def requestLevelTextWithExecution[R: TestFrameworkExecutionType](
      text: String,
      context: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?],
      r: => R
  ): TestFrameworkFragmentType

  private def updateStorage(ctx: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?], response: BaklavaResponseContext[?, ?, ?]): Unit = {
    storage.getAndUpdate(_ :+ (ctx -> response))
    ()
  }

  def storeResult(): Unit = {
    BaklavaDslFormatter.formatters.foreach(_.createChunk(getClass.getCanonicalName, storage.get()))
  }

  private val storage: AtomicReference[List[(BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]] =
    new AtomicReference(List.empty)
}
