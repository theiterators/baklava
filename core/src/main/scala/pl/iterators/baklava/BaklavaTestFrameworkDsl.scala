package pl.iterators.baklava

import java.util.Base64

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
    val ctx: BaklavaRequestContext[Nothing, Any, Any, Any, Any] = BaklavaRequestContext(
      symbolicPath = path,
      path = path,
      pathDescription = if (description.trim.isEmpty) None else Some(description.trim),
      pathSummary = if (summary.trim.isEmpty) None else Some(summary.trim),
      method = None,
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
      body = None,
      headers = BaklavaHttpHeaders(Map.empty),
      security = None,
      pathParameters = (),
      pathParametersProvided = (),
      queryParameters = (),
      queryParametersProvided = (),
      responseDescription = None
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

  def supports[PathParameters, QueryParameters](
      method: BaklavaHttpMethod,
      pathParameters: PathParameters = (),
      queryParameters: QueryParameters = (),
      description: String = "",
      summary: String = "",
      operationId: String = "",
      tags: Seq[String] = Seq.empty
  )(
      steps: BaklavaIntermediateTestCase[PathParameters, QueryParameters]*
  ): BaklavaMethodDefinition =
    new BaklavaMethodDefinition {
      override def apply(ctx: BaklavaRequestContext[Nothing, Any, Any, Any, Any]): TestFrameworkFragmentsType = {
        val finalSummary = if (summary.trim.isEmpty) "" else ": " + summary.trim
        val newCtx = BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit](
          symbolicPath = ctx.symbolicPath,
          path = ctx.path,
          pathDescription = ctx.pathDescription,
          pathSummary = ctx.pathSummary,
          method = Some(method),
          operationDescription = if (description.trim.isEmpty) None else Some(description.trim),
          operationSummary = if (summary.trim.isEmpty) None else Some(summary.trim),
          operationId = if (operationId.trim.isEmpty) None else Some(operationId.trim),
          operationTags = tags,
          body = None,
          headers = ctx.headers,
          security = None,
          pathParameters = pathParameters,
          pathParametersProvided = (),
          queryParameters = queryParameters,
          queryParametersProvided = (),
          responseDescription = None
        )
        methodLevelTextWithFragments(s"support ${method.value}" + finalSummary, newCtx, fragmentsFromSeq(steps.map(_.apply(newCtx))))
      }
    }

  trait BaklavaMethodDefinition {
    def apply(ctx: BaklavaRequestContext[Nothing, Any, Any, Any, Any]): TestFrameworkFragmentsType
  }

  trait BaklavaIntermediateTestCase[PathParameters, QueryParameters] {
    def apply(ctx: BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit]): TestFrameworkFragmentType
  }

  trait BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided] {
    def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters](
        r: BaklavaCaseContext[
          RequestBody,
          ResponseBody,
          PathParameters,
          PathParametersProvided,
          QueryParameters,
          QueryParametersProvided
        ] => R
    )(implicit
        providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
        provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided]
    ): BaklavaIntermediateTestCase[PathParameters, QueryParameters]
  }

  case class OnRequest[RequestBody: ToRequestBodyType, PathParametersProvided, QueryParametersProvided](
      body: RequestBody,
      headers: Map[String, String],
      security: Option[Security],
      pathParametersProvided: PathParametersProvided,
      queryParametersProvided: QueryParametersProvided
  ) {
    def respondsWith[ResponseBody: FromResponseBodyType](
        statusCode: BaklavaHttpStatus,
        description: String = ""
    ): BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided] =
      new BaklavaTestCase[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided] {
        override def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters](
            r: BaklavaCaseContext[
              RequestBody,
              ResponseBody,
              PathParameters,
              PathParametersProvided,
              QueryParameters,
              QueryParametersProvided
            ] => R
        )(implicit
            providePathParams: ProvidePathParams[PathParameters, PathParametersProvided],
            provideQueryParams: ProvideQueryParams[QueryParameters, QueryParametersProvided]
        ): BaklavaIntermediateTestCase[PathParameters, QueryParameters] =
          new BaklavaIntermediateTestCase[PathParameters, QueryParameters] {
            override def apply(
                requestContext: BaklavaRequestContext[Unit, PathParameters, Unit, QueryParameters, Unit]
            ): TestFrameworkFragmentType = {
              val finalDescription = if (description.trim.isEmpty) "" else ": " + description.trim
              var timesCalled: Int = 0
              val additionalHeaders = security match {
                case Some(Bearer(payload)) => Map("Authorization" -> s"Bearer $payload")
                case Some(Basic(id, secret)) =>
                  Map("Authorization" -> s"Basic ${Base64.getEncoder.encodeToString(s"$id:$secret".getBytes)}")
                case _ => Map.empty[String, String]
              }
              val finalRequestCtx =
                requestContext.copy(
                  path = provideQueryParams.apply(
                    requestContext.queryParameters,
                    queryParametersProvided,
                    providePathParams.apply(requestContext.pathParameters, pathParametersProvided, requestContext.path)
                  ),
                  body = if (body != EmptyBodyInstance) Some(body) else None,
                  headers = BaklavaHttpHeaders(headers ++ additionalHeaders),
                  security = security,
                  pathParametersProvided = pathParametersProvided,
                  queryParametersProvided = queryParametersProvided,
                  responseDescription = if (description.trim.isEmpty) None else Some(description.trim)
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
                        QueryParametersProvided
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
                        QueryParametersProvided
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
                    BaklavaGlobal.updateStorage(requestContext, responseContext)
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

  def onRequest: OnRequest[EmptyBody, Unit, Unit] =
    onRequest(EmptyBodyInstance: EmptyBody, Map.empty, None, (), ())(emptyToRequestBodyType)
  def onRequest[RequestBody: ToRequestBodyType, PathParametersProvided, QueryParametersProvided](
      body: RequestBody = EmptyBodyInstance: EmptyBody,
      headers: Map[String, String] = Map.empty,
      security: Option[Security] = None,
      pathParameters: PathParametersProvided = (),
      queryParameters: QueryParametersProvided = ()
  ): OnRequest[RequestBody, PathParametersProvided, QueryParametersProvided] =
    OnRequest(body, headers, security, pathParameters, queryParameters)

  def fragmentsFromSeq(fragments: Seq[TestFrameworkFragmentType]): TestFrameworkFragmentsType
  def concatFragments(fragments: Seq[TestFrameworkFragmentsType]): TestFrameworkFragmentsType
  def pathLevelTextWithFragments(
      text: String,
      context: BaklavaRequestContext[?, ?, ?, ?, ?],
      fragments: => TestFrameworkFragmentsType
  ): TestFrameworkFragmentsType
  def methodLevelTextWithFragments(
      text: String,
      context: BaklavaRequestContext[?, ?, ?, ?, ?],
      fragments: => TestFrameworkFragmentsType
  ): TestFrameworkFragmentsType
  def requestLevelTextWithExecution[R: TestFrameworkExecutionType](
      text: String,
      context: BaklavaRequestContext[?, ?, ?, ?, ?],
      r: => R
  ): TestFrameworkFragmentType
}
