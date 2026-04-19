package pl.iterators.baklava

import sttp.model.{Header => SttpHeader, Method, StatusCode}

import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import scala.reflect.ClassTag

class BaklavaAssertionException(message: String) extends RuntimeException(message)

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
      headers = Seq.empty,
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
      method: Method,
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
        val newCtx       = BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit, Headers, Unit](
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
        methodLevelTextWithFragments(s"support ${method.method}" + finalSummary, newCtx, fragmentsFromSeq(steps.map(_.apply(newCtx))))
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

  def maxBodyLengthInAssertion: Int = 8192

  private[baklava] def resolveRequestContext[
      RequestBody,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided,
      Headers,
      HeadersProvided
  ](
      requestContext: BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit, Headers, Unit],
      body: RequestBody,
      bodySchema: Schema[RequestBody],
      security: AppliedSecurity,
      pathParametersProvided: PathParametersProvided,
      queryParametersProvided: QueryParametersProvided,
      headersProvided: HeadersProvided,
      responseDescription: Option[String],
      responseHeaders: Seq[Header[?]]
  )(implicit
      providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
      provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided],
      provideHeaders: ProvideHeaders[Headers, HeadersProvided]
  ): BaklavaRequestContext[
    RequestBody,
    PathParameters,
    PathParametersProvided,
    QueryParameters,
    QueryParametersProvided,
    Headers,
    HeadersProvided
  ] = {
    val headersToInclude = provideHeaders.apply(requestContext.headersDefinition, headersProvided)
    val SecurityContribution(additionalSecurityHeaders, cookieContrib, queryContrib) =
      BaklavaTestFrameworkDsl.securityContribution(security)
    val headersWithCookieModifiedForSecurity: Map[String, String] =
      cookieContrib.fold(headersToInclude)(c => BaklavaTestFrameworkDsl.appendCookie(headersToInclude, c))
    val securityQueryParameters: Map[String, Seq[String]] = queryContrib

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
      bodySchema = Some(bodySchema),
      headers = (headersWithCookieModifiedForSecurity ++ additionalSecurityHeaders).map { case (n, v) => SttpHeader(n, v) }.toSeq,
      headersProvided = headersProvided,
      security = security,
      pathParametersProvided = pathParametersProvided,
      queryParametersProvided = queryParametersProvided,
      responseDescription = responseDescription,
      responseHeaders = responseHeaders
    )
  }

  private[baklava] def validateResponseAndStore[
      RequestBody,
      ResponseBody,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided,
      Headers,
      HeadersProvided
  ](
      body: RequestBody,
      requestContext: BaklavaRequestContext[
        RequestBody,
        PathParameters,
        PathParametersProvided,
        QueryParameters,
        QueryParametersProvided,
        Headers,
        HeadersProvided
      ],
      responseContext: BaklavaResponseContext[ResponseBody, HttpRequest, HttpResponse],
      requestBodySchema: Schema[RequestBody],
      responseBodySchema: Schema[ResponseBody],
      statusCode: StatusCode,
      expectedResponseHeaders: Seq[Header[?]],
      strictHeaderCheck: Boolean
  ): Unit = {
    body match {
      case b: FormOf[_] =>
        val allFields      = requestBodySchema.properties.keys.toList
        val requiredFields = requestBodySchema.properties
          .filter(_._2.required)
          .keys
          .toList

        val missingFields = requiredFields.filterNot(f => b.fields.exists(_._1 == f))
        if (missingFields.nonEmpty) {
          throw new BaklavaAssertionException(
            s"Missing required fields in form: ${missingFields.mkString(", ")}"
          )
        }
        val extraFields = b.fields.map(_._1).filterNot(allFields.contains)
        if (extraFields.nonEmpty) {
          throw new BaklavaAssertionException(
            s"Extra fields in form: ${extraFields.mkString(", ")}"
          )
        }
      case _ =>
    }

    if (responseContext.status != statusCode) {
      throw new BaklavaAssertionException(
        s"Expected ${statusCode.code} -> ${responseBodySchema.className}, but got ${responseContext.status.code} -> ${responseContext.responseBodyString.take(maxBodyLengthInAssertion)}"
      )
    }

    if (responseContext.responseBodyString.nonEmpty && responseBodySchema == Schema.emptyBodySchema) {
      throw new BaklavaAssertionException(
        "Expected empty response body, but got: " + responseContext.responseBodyString.take(maxBodyLengthInAssertion)
      )
    }

    expectedResponseHeaders.foreach { h =>
      val lowered = h.name.toLowerCase(java.util.Locale.ROOT)
      responseContext.headers.find(_.name.toLowerCase(java.util.Locale.ROOT) == lowered).map(_.value) match {
        case None        => throw new BaklavaAssertionException(s"Header ${h.name} not found but expected")
        case Some(value) =>
          val _ = h.tsm
            .unapply(value)
            .getOrElse(
              throw new BaklavaAssertionException(
                s"Header ${h.name} with value $value could not be parsed as ${h.schema.className}"
              )
            )
      }
    }
    if (strictHeaderCheck) {
      val expectedDistinctNames = expectedResponseHeaders.map(_.name.toLowerCase(java.util.Locale.ROOT)).distinct
      val actualDistinctNames   = responseContext.headers.map(_.name.toLowerCase(java.util.Locale.ROOT)).distinct
      if (expectedDistinctNames.length != actualDistinctNames.length) {
        throw new BaklavaAssertionException(
          s"Strict headers check is on, expected following headers: [${expectedResponseHeaders
              .map(h => h.name)
              .sorted
              .mkString(", ")}], but got: [${responseContext.headers.map(_.name).toList.sorted.mkString(", ")}]"
        )
      }
    }

    if (
      requestContext.security.security != NoopSecurity && !requestContext.securitySchemes
        .exists(_.security == requestContext.security.security)
    ) {
      throw new BaklavaAssertionException(
        s"Used security ${requestContext.security.security.`type`} is not defined in security schemes: [${requestContext.securitySchemes.map(ss => s"${ss.name} -> ${ss.security.`type`}").mkString(", ")}]"
      )
    }

    store(requestContext, responseContext.copy(bodySchema = Some(responseBodySchema)))
  }

  case class OnRequest[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
      body: RequestBody,
      security: AppliedSecurity,
      headersProvided: HeadersProvided,
      pathParametersProvided: PathParametersProvided,
      queryParametersProvided: QueryParametersProvided
  ) {
    def respondsWith[ResponseBody: FromResponseBodyType: Schema: ClassTag](
        statusCode: StatusCode,
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

              val finalRequestCtx = resolveRequestContext[
                RequestBody,
                PathParameters,
                PathParametersProvided,
                QueryParameters,
                QueryParametersProvided,
                Headers,
                HeadersProvided
              ](
                requestContext = requestContext,
                body = body,
                bodySchema = implicitly[Schema[RequestBody]],
                security = security,
                pathParametersProvided = pathParametersProvided,
                queryParametersProvided = queryParametersProvided,
                headersProvided = headersProvided,
                responseDescription = if (description.trim.isEmpty) None else Some(description.trim),
                responseHeaders = headers
              )

              requestLevelTextWithExecution(
                statusCode.code.toString + finalDescription,
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

                    validateResponseAndStore[
                      RequestBody,
                      ResponseBody,
                      PathParameters,
                      PathParametersProvided,
                      QueryParameters,
                      QueryParametersProvided,
                      Headers,
                      HeadersProvided
                    ](
                      body = body,
                      requestContext = requestContext,
                      responseContext = responseContext,
                      requestBodySchema = implicitly[Schema[RequestBody]],
                      responseBodySchema = implicitly[Schema[ResponseBody]],
                      statusCode = statusCode,
                      expectedResponseHeaders = headers,
                      strictHeaderCheck = strictHeaderCheck
                    )
                    responseContext
                  }
                  val baklavaCaseContext = BaklavaCaseContext(finalRequestCtx, wrappedPerformRequest)
                  r.andThen { x =>
                    if (timesCalled == 0) {
                      throw new BaklavaAssertionException("performRequest was not called in a test, one request should be made")
                    } else if (timesCalled > 1) {
                      throw new RuntimeException("performRequest was called multiple times in a test, only one request should be made")
                    }
                    x
                  }(baklavaCaseContext)
                }
              )
            }
          }
      }
  }

  trait WithSetupTestCase[S, RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] {
    def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters, Headers](
        r: (
            BaklavaCaseContext[
              RequestBody,
              ResponseBody,
              PathParameters,
              PathParametersProvided,
              QueryParameters,
              QueryParametersProvided,
              Headers,
              HeadersProvided
            ],
            S
        ) => R
    )(implicit
        providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
        provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided],
        provideHeaders: ProvideHeaders[Headers, HeadersProvided]
    ): BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers]
  }

  trait WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] {
    def respondsWith[ResponseBody: FromResponseBodyType: Schema: ClassTag](
        statusCode: StatusCode,
        headers: Seq[Header[?]] = Seq.empty,
        description: String = "",
        strictHeaderCheck: Boolean = strictHeaderCheckDefault
    ): WithSetupTestCase[S, RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
  }

  trait WithSetupBuilder[S] {

    /** Lazy form: construct the request from the setup value at test-execution time. */
    def request[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
        f: S => OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
    ): WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]

    /** Eager form: when the request values don't depend on the setup value, use the same named arguments as the top-level
      * `onRequest(...)`. The setup value still flows to `.assert`.
      */
    def onRequest[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
        body: RequestBody = EmptyBodyInstance: EmptyBody,
        security: AppliedSecurity = AppliedSecurity(NoopSecurity, Map.empty),
        pathParameters: PathParametersProvided = (),
        queryParameters: QueryParametersProvided = (),
        headers: HeadersProvided = ()
    ): WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
  }

  def withSetup[S](setup: => S): WithSetupBuilder[S] = {
    val setupThunk: () => S = () => setup
    new WithSetupBuilder[S] {
      override def request[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
          f: S => OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
      ): WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
        makeRequestBuilder(setupThunk, f)

      override def onRequest[RequestBody: ToRequestBodyType: Schema, PathParametersProvided, QueryParametersProvided, HeadersProvided](
          body: RequestBody,
          security: AppliedSecurity,
          pathParameters: PathParametersProvided,
          queryParameters: QueryParametersProvided,
          headers: HeadersProvided
      ): WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
        makeRequestBuilder(
          setupThunk,
          (_: S) =>
            OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided](
              body = body,
              security = security,
              headersProvided = headers,
              pathParametersProvided = pathParameters,
              queryParametersProvided = queryParameters
            )
        )
    }
  }

  private def makeRequestBuilder[
      S,
      RequestBody: ToRequestBodyType: Schema,
      PathParametersProvided,
      QueryParametersProvided,
      HeadersProvided
  ](
      setupThunk: () => S,
      requestFn: S => OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided]
  ): WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
    new WithSetupRequestBuilder[S, RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] {
      override def respondsWith[ResponseBody: FromResponseBodyType: Schema: ClassTag](
          statusCode: StatusCode,
          headers: Seq[Header[?]],
          description: String,
          strictHeaderCheck: Boolean
      ): WithSetupTestCase[S, RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
        makeWithSetupTestCase(setupThunk, requestFn, statusCode, headers, description, strictHeaderCheck)
    }

  private def makeWithSetupTestCase[
      S,
      RequestBody: ToRequestBodyType: Schema,
      ResponseBody: FromResponseBodyType: Schema: ClassTag,
      PathParametersProvided,
      QueryParametersProvided,
      HeadersProvided
  ](
      setupThunk: () => S,
      requestFn: S => OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided],
      statusCode: StatusCode,
      expectedResponseHeaders: Seq[Header[?]],
      description: String,
      strictHeaderCheck: Boolean
  ): WithSetupTestCase[S, RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] =
    new WithSetupTestCase[S, RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided, HeadersProvided] {
      override def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters, Headers](
          r: (
              BaklavaCaseContext[
                RequestBody,
                ResponseBody,
                PathParameters,
                PathParametersProvided,
                QueryParameters,
                QueryParametersProvided,
                Headers,
                HeadersProvided
              ],
              S
          ) => R
      )(implicit
          providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
          provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided],
          provideHeaders: ProvideHeaders[Headers, HeadersProvided]
      ): BaklavaIntermediateTestCase[PathParameters, QueryParameters, Headers] =
        withSetupExecute(
          setupThunk,
          requestFn,
          statusCode,
          expectedResponseHeaders,
          description,
          strictHeaderCheck,
          r
        )
    }

  private def withSetupExecute[
      S,
      R: TestFrameworkExecutionType,
      RequestBody: ToRequestBodyType: Schema,
      ResponseBody: FromResponseBodyType: Schema: ClassTag,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided,
      Headers,
      HeadersProvided
  ](
      setupThunk: () => S,
      requestFn: S => OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided, HeadersProvided],
      statusCode: StatusCode,
      expectedResponseHeaders: Seq[Header[?]],
      description: String,
      strictHeaderCheck: Boolean,
      assertFn: (
          BaklavaCaseContext[
            RequestBody,
            ResponseBody,
            PathParameters,
            PathParametersProvided,
            QueryParameters,
            QueryParametersProvided,
            Headers,
            HeadersProvided
          ],
          S
      ) => R
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

        requestLevelTextWithExecution(
          statusCode.code.toString + finalDescription,
          requestContext, {
            val setupValue = setupThunk()
            val onReq      = requestFn(setupValue)

            val finalRequestCtx = resolveRequestContext[
              RequestBody,
              PathParameters,
              PathParametersProvided,
              QueryParameters,
              QueryParametersProvided,
              Headers,
              HeadersProvided
            ](
              requestContext = requestContext,
              body = onReq.body,
              bodySchema = implicitly[Schema[RequestBody]],
              security = onReq.security,
              pathParametersProvided = onReq.pathParametersProvided,
              queryParametersProvided = onReq.queryParametersProvided,
              headersProvided = onReq.headersProvided,
              responseDescription = if (description.trim.isEmpty) None else Some(description.trim),
              responseHeaders = expectedResponseHeaders
            )

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

              validateResponseAndStore[
                RequestBody,
                ResponseBody,
                PathParameters,
                PathParametersProvided,
                QueryParameters,
                QueryParametersProvided,
                Headers,
                HeadersProvided
              ](
                body = onReq.body,
                requestContext = requestContext,
                responseContext = responseContext,
                requestBodySchema = implicitly[Schema[RequestBody]],
                responseBodySchema = implicitly[Schema[ResponseBody]],
                statusCode = statusCode,
                expectedResponseHeaders = expectedResponseHeaders,
                strictHeaderCheck = strictHeaderCheck
              )
              responseContext
            }
            val baklavaCaseContext = BaklavaCaseContext(finalRequestCtx, wrappedPerformRequest)
            val wrappedAssert: BaklavaCaseContext[
              RequestBody,
              ResponseBody,
              PathParameters,
              PathParametersProvided,
              QueryParameters,
              QueryParametersProvided,
              Headers,
              HeadersProvided
            ] => R = ctx => assertFn(ctx, setupValue)
            wrappedAssert.andThen { x =>
              if (timesCalled == 0) {
                throw new BaklavaAssertionException("performRequest was not called in a test, one request should be made")
              } else if (timesCalled > 1) {
                throw new RuntimeException("performRequest was called multiple times in a test, only one request should be made")
              }
              x
            }(baklavaCaseContext)
          }
        )
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

  protected def store(request: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?], response: BaklavaResponseContext[?, ?, ?]): Unit = {
    BaklavaSerialize.serializeCall(request, response) match {
      case scala.util.Failure(exception) =>
        System.err.println(s"Failed to serialize call: $exception")
      case scala.util.Success(_) => // Success, no action needed
    }
  }
}

/** The header, cookie, and query-parameter contribution a single `AppliedSecurity` makes to an outgoing request.
  *
  *   - `headers`: additional headers to merge on top of whatever the caller already set (e.g. `Authorization: Bearer …`).
  *   - `cookie`: an optional `name=value` segment to append to any existing `Cookie` header (or to create one).
  *   - `queryParameters`: query params to merge into the URI (e.g. `?api_key=…`).
  */
final case class SecurityContribution(
    headers: Map[String, String],
    cookie: Option[(String, String)],
    queryParameters: Map[String, Seq[String]]
)

object BaklavaTestFrameworkDsl {

  /** Compute everything an `AppliedSecurity` needs to inject into the outgoing request, in a single pass. Previously this was three
    * separate `match` expressions over the same value — easy to drift out of sync.
    */
  def securityContribution(security: AppliedSecurity): SecurityContribution = {
    def bearerAuth(token: String): Map[String, String]             = Map("Authorization" -> s"Bearer $token")
    def basicAuth(id: String, secret: String): Map[String, String] =
      Map("Authorization" -> s"Basic ${Base64.getEncoder.encodeToString(s"$id:$secret".getBytes)}")

    security match {
      case AppliedSecurity(_: HttpBearer, p)     => SecurityContribution(bearerAuth(p("token")), None, Map.empty)
      case AppliedSecurity(_: HttpBasic, p)      => SecurityContribution(basicAuth(p("id"), p("secret")), None, Map.empty)
      case AppliedSecurity(s: ApiKeyInHeader, p) => SecurityContribution(Map(s.name -> p("apiKey")), None, Map.empty)
      case AppliedSecurity(s: ApiKeyInQuery, p)  =>
        SecurityContribution(Map.empty, None, Map(s.name -> Seq(p("apiKey"))))
      case AppliedSecurity(s: ApiKeyInCookie, p)        => SecurityContribution(Map.empty, Some(s.name -> p("apiKey")), Map.empty)
      case AppliedSecurity(_: MutualTls, _)             => SecurityContribution(Map.empty, None, Map.empty)
      case AppliedSecurity(_: OpenIdConnectInBearer, p) => SecurityContribution(bearerAuth(p("token")), None, Map.empty)
      case AppliedSecurity(_: OpenIdConnectInCookie, p) =>
        SecurityContribution(Map.empty, Some(p("name") -> p("token")), Map.empty)
      case AppliedSecurity(_: OAuth2InBearer, p) => SecurityContribution(bearerAuth(p("token")), None, Map.empty)
      case AppliedSecurity(_: OAuth2InCookie, p) =>
        SecurityContribution(Map.empty, Some(p("name") -> p("token")), Map.empty)
      case AppliedSecurity(NoopSecurity, _) => SecurityContribution(Map.empty, None, Map.empty)
    }
  }

  /** Append a `name=value` segment to the `Cookie` header in the map (case-insensitive key lookup), or create a fresh `Cookie` header if
    * none exists. Preserves any pre-existing cookie(s) via `;` concatenation.
    */
  def appendCookie(headers: Map[String, String], cookie: (String, String)): Map[String, String] = {
    val (name, value) = cookie
    headers.find(_._1.toLowerCase == "cookie") match {
      case Some((_, existing)) => headers + ("Cookie" -> s"$existing; $name=$value")
      case None                => headers + ("Cookie" -> s"$name=$value")
    }
  }
}

//todo better name
trait BaklavaTestFrameworkDslDebug[
    RouteType,
    ToRequestBodyType[_],
    FromResponseBodyType[_],
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType[_]
] extends BaklavaTestFrameworkDsl[
      RouteType,
      ToRequestBodyType,
      FromResponseBodyType,
      TestFrameworkFragmentType,
      TestFrameworkFragmentsType,
      TestFrameworkExecutionType
    ] {
  this: BaklavaHttpDsl[
    RouteType,
    ToRequestBodyType,
    FromResponseBodyType,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  private val atomicSeq: AtomicReference[Seq[BaklavaSerializableCall]] =
    new AtomicReference(Seq.empty)

  override protected def store(request: BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?], response: BaklavaResponseContext[?, ?, ?]): Unit = {
    val call = BaklavaSerializableCall(BaklavaRequestContextSerializable(request), BaklavaResponseContextSerializable(response))
    atomicSeq.updateAndGet(seq => seq :+ call)
    ()
  }

  def listCalls: Seq[BaklavaSerializableCall] = atomicSeq.get()

}
