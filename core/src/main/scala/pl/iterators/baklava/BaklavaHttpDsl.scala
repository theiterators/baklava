package pl.iterators.baklava

import pl.iterators.kebs.core.enums.EnumLike
import pl.iterators.kebs.core.macros.ValueClassLike

import scala.reflect.ClassTag

sealed trait EmptyBody

case object EmptyBodyInstance extends EmptyBody

case class BaklavaHttpMethod(value: String)

case class BaklavaHttpProtocol(protocol: String)

case class BaklavaHttpStatus(status: Int)

case class BaklavaHttpHeaders(headers: Map[String, String])

case class Baklava2Context[Body, PathParameters, PathParametersProvided, QueryParameters, QueryParametersProvided](
    symbolicPath: String,
    path: String,
    method: Option[BaklavaHttpMethod],
    body: Option[Body],
    headers: BaklavaHttpHeaders,
    security: Option[Security],
    pathParameters: PathParameters,
    pathParametersProvided: PathParametersProvided,
    queryParameters: QueryParameters,
    queryParametersProvided: QueryParametersProvided
)

case class Baklava2ResponseContext[ResponseBody](
    protocol: BaklavaHttpProtocol,
    status: BaklavaHttpStatus,
    headers: BaklavaHttpHeaders,
    body: ResponseBody
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

trait BaklavaHttpDsl[
    RouteType,
    ToRequestBodyType[_],
    FromResponseBodyType[_],
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType[_]
] {
  this: BaklavaTestFrameworkDsl[
    RouteType,
    ToRequestBodyType,
    FromResponseBodyType,
    TestFrameworkFragmentType,
    TestFrameworkFragmentsType,
    TestFrameworkExecutionType
  ] =>

  type HttpResponse
  type HttpProtocol
  type HttpStatusCode
  type HttpMethod
  type HttpHeaders

  trait ToQueryParam[T] {
    def apply(t: T): Seq[String]
  }

  case class QueryParam[T](name: String)(implicit val tsm: ToQueryParam[T]) {
    type Underlying = T
  }

  trait ToPathParam[T] {
    def apply(t: T): String
  }

  case class PathParam[T](name: String)(implicit val tsm: ToPathParam[T]) {
    type Underlying = T
  }

  sealed trait ProvidePathParams[T, U] {
    def apply(
        pathParams: T,
        pathParamsProvided: U,
        uri: String
    ): String
  }

  sealed trait ProvideQueryParams[T, U] {
    def apply(
        queryParameters: T,
        queryParametersProvided: U,
        uri: String
    ): String
  }

  case class Baklava2CaseContext[
      RequestBody,
      ResponseBody,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided
  ](
      ctx: Baklava2Context[RequestBody, PathParameters, PathParametersProvided, QueryParameters, QueryParametersProvided],
      _performRequest: (
          Baklava2Context[RequestBody, PathParameters, PathParametersProvided, QueryParameters, QueryParametersProvided],
          RouteType
      ) => Baklava2ResponseContext[ResponseBody]
  ) {
    def performRequest(route: RouteType): Baklava2ResponseContext[ResponseBody] = _performRequest(ctx, route)
  }

  def q[T](name: String)(implicit tsm: ToQueryParam[T]): QueryParam[T] = QueryParam[T](name)
  def p[T](name: String)(implicit tsm: ToPathParam[T]): PathParam[T]   = PathParam[T](name)(tsm)

  implicit val toPathParamString: ToPathParam[String] = new ToPathParam[String] {
    override def apply(t: String): String = t
  }

  implicit val toPathParamUUID: ToPathParam[java.util.UUID] = new ToPathParam[java.util.UUID] {
    override def apply(t: java.util.UUID): String = t.toString
  }

  implicit def toPathParamValueClassLike[T, U](implicit ev: ValueClassLike[T, U], tsm: ToPathParam[U]): ToPathParam[T] =
    new ToPathParam[T] {
      override def apply(t: T): String = tsm(ev.unapply(t))
    }

  implicit val toQueryParamString: ToQueryParam[String] = new ToQueryParam[String] {
    override def apply(t: String): Seq[String] = Seq(t)
  }

  implicit val toQueryParamInt: ToQueryParam[Int] = new ToQueryParam[Int] {
    override def apply(t: Int): Seq[String] = Seq(t.toString)
  }

  implicit val toQueryParamUUID: ToQueryParam[java.util.UUID] = new ToQueryParam[java.util.UUID] {
    override def apply(t: java.util.UUID): Seq[String] = Seq(t.toString)
  }

  implicit def toQueryParamValueClassLike[T, U](implicit ev: ValueClassLike[T, U], tsm: ToQueryParam[U]): ToQueryParam[T] =
    new ToQueryParam[T] {
      override def apply(t: T): Seq[String] = tsm(ev.unapply(t))
    }

  implicit def toQueryParamSeq[T](implicit tsm: ToQueryParam[T]): ToQueryParam[Seq[T]] = new ToQueryParam[Seq[T]] {
    override def apply(t: Seq[T]): Seq[String] = t.flatMap(tsm.apply)
  }

  implicit def toQueryParamEnum[T](implicit _enum: EnumLike[T]): ToQueryParam[T] = new ToQueryParam[T] {
    override def apply(t: T): Seq[String] = {
      val _ = _enum // fix warning
      Seq(t.toString)
    }
  }

  implicit def toQueryParamOption[T](implicit tsm: ToQueryParam[T]): ToQueryParam[Option[T]] = new ToQueryParam[Option[T]] {
    override def apply(t: Option[T]): Seq[String] = t.map(tsm.apply).getOrElse(Seq.empty)
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
    ): String = uri.replace(s"{${pathParams.name}}", pathParams.tsm(pathParamsProvided))
  }

  implicit def provideQueryParamsByUnit[T]: ProvideQueryParams[T, Unit] = new ProvideQueryParams[T, Unit] {
    override def apply(
        queryParameters: T,
        queryParametersProvided: Unit,
        uri: String
    ): String = uri
  }

  implicit def provideQueryParamsSingleValue[T]: ProvideQueryParams[QueryParam[T], T] = new ProvideQueryParams[QueryParam[T], T] {
    override def apply(
        queryParameters: QueryParam[T],
        queryParametersProvided: T,
        uri: String
    ): String = addQueryParametersToUri(uri, Map(queryParameters.name -> queryParameters.tsm(queryParametersProvided)))
  }

  implicit def provideQueryParams2[A, B]: ProvideQueryParams[(QueryParam[A], QueryParam[B]), (A, B)] =
    new ProvideQueryParams[(QueryParam[A], QueryParam[B]), (A, B)] {
      override def apply(
          queryParameters: (QueryParam[A], QueryParam[B]),
          queryParametersProvided: (A, B),
          uri: String
      ): String = {
        val (a, b) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b)
          )
        )
      }
    }

  implicit def provideQueryParams3[A, B, C]: ProvideQueryParams[(QueryParam[A], QueryParam[B], QueryParam[C]), (A, B, C)] =
    new ProvideQueryParams[(QueryParam[A], QueryParam[B], QueryParam[C]), (A, B, C)] {
      override def apply(
          queryParameters: (QueryParam[A], QueryParam[B], QueryParam[C]),
          queryParametersProvided: (A, B, C),
          uri: String
      ): String = {
        val (a, b, c) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c)
          )
        )
      }
    }

  implicit def provideQueryParams4[A, B, C, D]
      : ProvideQueryParams[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D]), (A, B, C, D)] =
    new ProvideQueryParams[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D]), (A, B, C, D)] {
      override def apply(
          queryParameters: (QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D]),
          queryParametersProvided: (A, B, C, D),
          uri: String
      ): String = {
        val (a, b, c, d) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c),
            queryParameters._4.name -> queryParameters._4.tsm(d)
          )
        )
      }
    }

  // created by chatgpt, check later
  private def addQueryParametersToUri(uri: String, queryParameters: Map[String, Seq[String]]): String = {
    if (queryParameters.isEmpty) {
      uri
    } else {
      val (baseUri, fragment) = uri.splitAt(uri.indexOf("#") match {
        case -1  => uri.length // No fragment
        case idx => idx
      })

      val baseWithQuery = if (baseUri.contains("?")) s"$baseUri&" else s"$baseUri?"
      val queryString = queryParameters
        .flatMap { case (param, values) =>
          values.map(value => s"${encode(param)}=${encode(value)}")
        }
        .mkString("&")

      baseWithQuery + queryString + fragment
    }
  }

  private def encode(value: String): String = {
    java.net.URLEncoder.encode(value, "UTF-8")
  }

  def testCase[PathParameters, QueryParameters](
      s: Baklava2CaseStep[PathParameters, QueryParameters]
  ): Baklava2CaseStep[PathParameters, QueryParameters] = s

  def performRequest[
      RequestBody: ToRequestBodyType,
      ResponseBody: FromResponseBodyType: ClassTag,
      PathParameters,
      PathParametersProvided,
      QueryParameters,
      QueryParametersProvided
  ](
      ctx: Baklava2Context[RequestBody, PathParameters, PathParametersProvided, QueryParameters, QueryParametersProvided],
      route: RouteType
  ): Baklava2ResponseContext[ResponseBody]

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

  implicit def httpResponseToBaklavaResponseContext[T: FromResponseBodyType](response: HttpResponse): Baklava2ResponseContext[T]
}
