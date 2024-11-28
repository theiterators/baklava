package pl.iterators.baklava

import scala.reflect.ClassTag

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

  def path(path: String)(steps: Baklava2MethodStep*): TestFrameworkFragmentsType = {
    val ctx: Baklava2Context[Nothing, Any, Any, Any, Any] = Baklava2Context(
      symbolicPath = path,
      path = path,
      method = None,
      body = None,
      headers = BaklavaHttpHeaders(Map.empty),
      security = None,
      pathParameters = (),
      pathParametersProvided = (),
      queryParameters = (),
      queryParametersProvided = ()
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
      description: String = ""
  )(
      steps: Baklava2CaseStep[PathParameters, QueryParameters]*
  ): Baklava2MethodStep =
    new Baklava2MethodStep {
      override def apply(ctx: Baklava2Context[Nothing, Any, Any, Any, Any]): TestFrameworkFragmentsType = {
        val finalDescription = if (description.trim.isEmpty) "" else ": " + description.trim
        val newCtx = Baklava2Context[Unit, PathParameters, Unit, QueryParameters, Unit](
          symbolicPath = ctx.symbolicPath,
          path = ctx.path,
          method = Some(method),
          body = None,
          headers = ctx.headers,
          security = None,
          pathParameters = pathParameters,
          pathParametersProvided = (),
          queryParameters = queryParameters,
          queryParametersProvided = ()
        )
        methodLevelTextWithFragments(s"support ${method.value}" + finalDescription, newCtx, fragmentsFromSeq(steps.map(_.apply(newCtx))))
      }
    }

  trait Baklava2MethodStep {
    def apply(ctx: Baklava2Context[Nothing, Any, Any, Any, Any]): TestFrameworkFragmentsType
  }

  trait Baklava2CaseStep[PathParameters, QueryParameters] {
    def apply(ctx: Baklava2Context[Unit, PathParameters, Unit, QueryParameters, Unit]): TestFrameworkFragmentType
  }

  trait Baklava2SimpleCaseStep[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided] {
    def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters](
        r: Baklava2CaseContext[
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
    ): Baklava2CaseStep[PathParameters, QueryParameters]
  }

  case class OnRequest[RequestBody: ToRequestBodyType, PathParametersProvided, QueryParametersProvided](
      body: RequestBody,
      headers: Map[String, String],
      security: Option[Security],
      pathParametersProvided: PathParametersProvided,
      queryParametersProvided: QueryParametersProvided
  ) {
    def respondsWith[ResponseBody: FromResponseBodyType: ClassTag](
        statusCode: BaklavaHttpStatus,
        description: String = ""
    ): Baklava2SimpleCaseStep[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided] =
      new Baklava2SimpleCaseStep[RequestBody, ResponseBody, PathParametersProvided, QueryParametersProvided] {
        override def assert[R: TestFrameworkExecutionType, PathParameters, QueryParameters](
            r: Baklava2CaseContext[
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
        ): Baklava2CaseStep[PathParameters, QueryParameters] =
          new Baklava2CaseStep[PathParameters, QueryParameters] {
            override def apply(ctx: Baklava2Context[Unit, PathParameters, Unit, QueryParameters, Unit]): TestFrameworkFragmentType = {
              val finalDescription = if (description.trim.isEmpty) "" else ": " + description.trim
              var timesCalled: Int = 0
              val additionalHeaders = security match {
                case Some(Bearer(payload)) => Map("Authorization" -> s"Bearer $payload")
                case _                     => Map.empty[String, String]
              }
              val finalCtx =
                ctx.copy(
                  path = provideQueryParams.apply(
                    ctx.queryParameters,
                    queryParametersProvided,
                    providePathParams.apply(ctx.pathParameters, pathParametersProvided, ctx.path)
                  ),
                  body = if (body != BaklavaEmptyBody) Some(body) else None,
                  headers = BaklavaHttpHeaders(headers ++ additionalHeaders),
                  security = security,
                  pathParametersProvided = pathParametersProvided,
                  queryParametersProvided = queryParametersProvided
                )

              requestLevelTextWithExecution(
                statusCode.status.toString + finalDescription,
                finalCtx, {
                  val wrappedPerformRequest = (
                      ctx: Baklava2Context[RequestBody, PathParameters, PathParametersProvided, QueryParameters, QueryParametersProvided],
                      route: RouteType
                  ) => {
                    val response =
                      performRequest[
                        RequestBody,
                        ResponseBody,
                        PathParameters,
                        PathParametersProvided,
                        QueryParameters,
                        QueryParametersProvided
                      ](
                        ctx,
                        route
                      )
                    timesCalled += 1
                    if (response.status != statusCode) {
                      throw new RuntimeException(
                        s"Expected status code $statusCode, but got ${response.status}"
                      )
                    }
                    BaklavaGlobal.updateStorage(ctx, response)
                    response
                  }
                  val baklava2CaseContext = Baklava2CaseContext(finalCtx, wrappedPerformRequest)
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

  def onRequest: OnRequest[BaklavaEmptyBody.type, Unit, Unit] =
    onRequest(BaklavaEmptyBody, Map.empty, None, (), ())(emptyToResponseBodyType)
  def onRequest[RequestBody: ToRequestBodyType, PathParametersProvided, QueryParametersProvided](
      body: RequestBody = "",
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
      context: Baklava2Context[?, ?, ?, ?, ?],
      fragments: => TestFrameworkFragmentsType
  ): TestFrameworkFragmentsType
  def methodLevelTextWithFragments(
      text: String,
      context: Baklava2Context[?, ?, ?, ?, ?],
      fragments: => TestFrameworkFragmentsType
  ): TestFrameworkFragmentsType
  def requestLevelTextWithExecution[R: TestFrameworkExecutionType](
      text: String,
      context: Baklava2Context[?, ?, ?, ?, ?],
      r: => R
  ): TestFrameworkFragmentType
}
