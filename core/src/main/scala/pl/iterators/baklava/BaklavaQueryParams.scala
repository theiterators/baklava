package pl.iterators.baklava

trait ToQueryParam[T] {
  def apply(t: T): Seq[String]
}

case class QueryParam[T](name: String, description: Option[String])(implicit val tsm: ToQueryParam[T], val schema: Schema[T]) {
  type Underlying = T
}

trait ToQueryParamSeq[T] {
  def apply(t: T): Seq[QueryParam[?]]
}

trait ProvideQueryParams[T, U] {
  def apply(
      queryParameters: T,
      queryParametersProvided: U,
      uri: String
  ): String
}

trait BaklavaQueryParams {
  def q[T](name: String, description: String = "")(implicit tsm: ToQueryParam[T], schema: Schema[T]): QueryParam[T] =
    QueryParam[T](name, if (description.trim.isEmpty) None else Some(description.trim))

  implicit val stringToQueryParam: ToQueryParam[String] = new ToQueryParam[String] {
    override def apply(t: String): Seq[String] = Seq(t)
  }

  implicit val intToQueryParam: ToQueryParam[Int] = new ToQueryParam[Int] {
    override def apply(t: Int): Seq[String] = Seq(t.toString)
  }

  implicit val longToQueryParam: ToQueryParam[Long] = new ToQueryParam[Long] {
    override def apply(t: Long): Seq[String] = Seq(t.toString)
  }

  implicit val doubleToQueryParam: ToQueryParam[Double] = new ToQueryParam[Double] {
    override def apply(t: Double): Seq[String] = Seq(t.toString)
  }

  implicit val floatToQueryParam: ToQueryParam[Float] = new ToQueryParam[Float] {
    override def apply(t: Float): Seq[String] = Seq(t.toString)
  }

  implicit val booleanToQueryParam: ToQueryParam[Boolean] = new ToQueryParam[Boolean] {
    override def apply(t: Boolean): Seq[String] = Seq(t.toString)
  }

  implicit val byteToQueryParam: ToQueryParam[Byte] = new ToQueryParam[Byte] {
    override def apply(t: Byte): Seq[String] = Seq(t.toString)
  }

  implicit val shortToQueryParam: ToQueryParam[Short] = new ToQueryParam[Short] {
    override def apply(t: Short): Seq[String] = Seq(t.toString)
  }

  implicit val charToQueryParam: ToQueryParam[Char] = new ToQueryParam[Char] {
    override def apply(t: Char): Seq[String] = Seq(t.toString)
  }

  implicit val bigDecimalToQueryParam: ToQueryParam[BigDecimal] = new ToQueryParam[BigDecimal] {
    override def apply(t: BigDecimal): Seq[String] = Seq(t.toString)
  }

  implicit val uuidToQueryParam: ToQueryParam[java.util.UUID] = new ToQueryParam[java.util.UUID] {
    override def apply(t: java.util.UUID): Seq[String] = Seq(t.toString)
  }

  implicit def seqToQueryParam[T](implicit tsm: ToQueryParam[T]): ToQueryParam[Seq[T]] = new ToQueryParam[Seq[T]] {
    override def apply(t: Seq[T]): Seq[String] = t.flatMap(tsm.apply)
  }

  implicit def optionToQueryParam[T](implicit tsm: ToQueryParam[T]): ToQueryParam[Option[T]] = new ToQueryParam[Option[T]] {
    override def apply(t: Option[T]): Seq[String] = t.map(tsm.apply).getOrElse(Seq.empty)
  }

  // TODO: arrays, collections

  implicit val unitToQueryParamSeq: ToQueryParamSeq[Unit] = (_: Unit) => Seq.empty

  implicit def singleValueToQueryParamSeq[T]: ToQueryParamSeq[QueryParam[T]] = (t: QueryParam[T]) => Seq(t)

  implicit def tuple2ToQueryParamSeq[A, B]: ToQueryParamSeq[(QueryParam[A], QueryParam[B])] =
    (t: (QueryParam[A], QueryParam[B])) => Seq(t._1, t._2)

  implicit def tuple3ToQueryParamSeq[A, B, C]: ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C])] =
    (t: (QueryParam[A], QueryParam[B], QueryParam[C])) => Seq(t._1, t._2, t._3)

  implicit def tuple4ToQueryParamSeq[A, B, C, D]: ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D])] =
    (t: (QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D])) => Seq(t._1, t._2, t._3, t._4)

  implicit def tuple5ToQueryParamSeq[A, B, C, D, E]
      : ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D], QueryParam[E])] =
    t => Seq(t._1, t._2, t._3, t._4, t._5)

  implicit def tuple6ToQueryParamSeq[A, B, C, D, E, F]
      : ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D], QueryParam[E], QueryParam[F])] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6)

  implicit def tuple7ToQueryParamSeq[A, B, C, D, E, F, G]
      : ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D], QueryParam[E], QueryParam[F], QueryParam[G])] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7)

  implicit def tuple8ToQueryParamSeq[A, B, C, D, E, F, G, H]: ToQueryParamSeq[
    (QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D], QueryParam[E], QueryParam[F], QueryParam[G], QueryParam[H])
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8)

  implicit def tuple9ToQueryParamSeq[A, B, C, D, E, F, G, H, I]: ToQueryParamSeq[
    (QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D], QueryParam[E], QueryParam[F], QueryParam[G], QueryParam[H], QueryParam[I])
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9)

  implicit def tuple10ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10)

  implicit def tuple11ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11)

  implicit def tuple12ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12)

  implicit def tuple13ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13)

  implicit def tuple14ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14)

  implicit def tuple15ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15)

  implicit def tuple16ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16)

  implicit def tuple17ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P],
        QueryParam[Q]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17)

  implicit def tuple18ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P],
        QueryParam[Q],
        QueryParam[R]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18)

  implicit def tuple19ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P],
        QueryParam[Q],
        QueryParam[R],
        QueryParam[S]
    )
  ] =
    t => Seq(t._1, t._2, t._3, t._4, t._5, t._6, t._7, t._8, t._9, t._10, t._11, t._12, t._13, t._14, t._15, t._16, t._17, t._18, t._19)

  implicit def tuple20ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P],
        QueryParam[Q],
        QueryParam[R],
        QueryParam[S],
        QueryParam[T]
    )
  ] =
    t =>
      Seq(
        t._1,
        t._2,
        t._3,
        t._4,
        t._5,
        t._6,
        t._7,
        t._8,
        t._9,
        t._10,
        t._11,
        t._12,
        t._13,
        t._14,
        t._15,
        t._16,
        t._17,
        t._18,
        t._19,
        t._20
      )

  implicit def tuple21ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P],
        QueryParam[Q],
        QueryParam[R],
        QueryParam[S],
        QueryParam[T],
        QueryParam[U]
    )
  ] =
    t =>
      Seq(
        t._1,
        t._2,
        t._3,
        t._4,
        t._5,
        t._6,
        t._7,
        t._8,
        t._9,
        t._10,
        t._11,
        t._12,
        t._13,
        t._14,
        t._15,
        t._16,
        t._17,
        t._18,
        t._19,
        t._20,
        t._21
      )

  implicit def tuple22ToQueryParamSeq[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V]: ToQueryParamSeq[
    (
        QueryParam[A],
        QueryParam[B],
        QueryParam[C],
        QueryParam[D],
        QueryParam[E],
        QueryParam[F],
        QueryParam[G],
        QueryParam[H],
        QueryParam[I],
        QueryParam[J],
        QueryParam[K],
        QueryParam[L],
        QueryParam[M],
        QueryParam[N],
        QueryParam[O],
        QueryParam[P],
        QueryParam[Q],
        QueryParam[R],
        QueryParam[S],
        QueryParam[T],
        QueryParam[U],
        QueryParam[V]
    )
  ] =
    t =>
      Seq(
        t._1,
        t._2,
        t._3,
        t._4,
        t._5,
        t._6,
        t._7,
        t._8,
        t._9,
        t._10,
        t._11,
        t._12,
        t._13,
        t._14,
        t._15,
        t._16,
        t._17,
        t._18,
        t._19,
        t._20,
        t._21,
        t._22
      )

  implicit def provideQueryParamsByUnit[T]: ProvideQueryParams[T, Unit] = new ProvideQueryParams[T, Unit] {
    override def apply(
        queryParameters: T,
        queryParametersProvided: Unit,
        uri: String
    ): String = uri
  }

  implicit def provideQueryParamsSingleValue[T, U <: T]: ProvideQueryParams[QueryParam[T], U] = new ProvideQueryParams[QueryParam[T], U] {
    override def apply(
        queryParameters: QueryParam[T],
        queryParametersProvided: U,
        uri: String
    ): String = addQueryParametersToUri(uri, Map(queryParameters.name -> queryParameters.tsm(queryParametersProvided)))
  }

  implicit def provideQueryParams2[T1, T2, U1 <: T1, U2 <: T2]: ProvideQueryParams[(QueryParam[T1], QueryParam[T2]), (U1, U2)] =
    new ProvideQueryParams[(QueryParam[T1], QueryParam[T2]), (U1, U2)] {
      override def apply(
          queryParameters: (QueryParam[T1], QueryParam[T2]),
          queryParametersProvided: (U1, U2),
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

  implicit def provideQueryParams3[T1, T2, T3, U1 <: T1, U2 <: T2, U3 <: T3]
      : ProvideQueryParams[(QueryParam[T1], QueryParam[T2], QueryParam[T3]), (U1, U2, U3)] =
    new ProvideQueryParams[(QueryParam[T1], QueryParam[T2], QueryParam[T3]), (U1, U2, U3)] {
      override def apply(
          queryParameters: (QueryParam[T1], QueryParam[T2], QueryParam[T3]),
          queryParametersProvided: (U1, U2, U3),
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

  implicit def provideQueryParams4[T1, T2, T3, T4, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4]
      : ProvideQueryParams[(QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4]), (U1, U2, U3, U4)] =
    new ProvideQueryParams[(QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4]), (U1, U2, U3, U4)] {
      override def apply(
          queryParameters: (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4]),
          queryParametersProvided: (U1, U2, U3, U4),
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

  implicit def provideQueryParams5[T1, T2, T3, T4, T5, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5]
      : ProvideQueryParams[(QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5]), (U1, U2, U3, U4, U5)] =
    new ProvideQueryParams[(QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5]), (U1, U2, U3, U4, U5)] {
      override def apply(
          queryParameters: (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5]),
          queryParametersProvided: (U1, U2, U3, U4, U5),
          uri: String
      ): String = {
        val (a, b, c, d, e) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c),
            queryParameters._4.name -> queryParameters._4.tsm(d),
            queryParameters._5.name -> queryParameters._5.tsm(e)
          )
        )
      }
    }

  implicit def provideQueryParams6[T1, T2, T3, T4, T5, T6, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5, U6 <: T6]: ProvideQueryParams[
    (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6]),
    (U1, U2, U3, U4, U5, U6)
  ] =
    new ProvideQueryParams[
      (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6]),
      (U1, U2, U3, U4, U5, U6)
    ] {
      override def apply(
          queryParameters: (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6]),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6),
          uri: String
      ): String = {
        val (a, b, c, d, e, f) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c),
            queryParameters._4.name -> queryParameters._4.tsm(d),
            queryParameters._5.name -> queryParameters._5.tsm(e),
            queryParameters._6.name -> queryParameters._6.tsm(f)
          )
        )
      }
    }

  implicit def provideQueryParams7[T1, T2, T3, T4, T5, T6, T7, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5, U6 <: T6, U7 <: T7]
      : ProvideQueryParams[
        (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6], QueryParam[T7]),
        (U1, U2, U3, U4, U5, U6, U7)
      ] =
    new ProvideQueryParams[
      (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6], QueryParam[T7]),
      (U1, U2, U3, U4, U5, U6, U7)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c),
            queryParameters._4.name -> queryParameters._4.tsm(d),
            queryParameters._5.name -> queryParameters._5.tsm(e),
            queryParameters._6.name -> queryParameters._6.tsm(f),
            queryParameters._7.name -> queryParameters._7.tsm(g)
          )
        )
      }
    }

  implicit def provideQueryParams8[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8
  ]: ProvideQueryParams[
    (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6], QueryParam[T7], QueryParam[T8]),
    (U1, U2, U3, U4, U5, U6, U7, U8)
  ] =
    new ProvideQueryParams[
      (QueryParam[T1], QueryParam[T2], QueryParam[T3], QueryParam[T4], QueryParam[T5], QueryParam[T6], QueryParam[T7], QueryParam[T8]),
      (U1, U2, U3, U4, U5, U6, U7, U8)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c),
            queryParameters._4.name -> queryParameters._4.tsm(d),
            queryParameters._5.name -> queryParameters._5.tsm(e),
            queryParameters._6.name -> queryParameters._6.tsm(f),
            queryParameters._7.name -> queryParameters._7.tsm(g),
            queryParameters._8.name -> queryParameters._8.tsm(h)
          )
        )
      }
    }

  implicit def provideQueryParams9[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name -> queryParameters._1.tsm(a),
            queryParameters._2.name -> queryParameters._2.tsm(b),
            queryParameters._3.name -> queryParameters._3.tsm(c),
            queryParameters._4.name -> queryParameters._4.tsm(d),
            queryParameters._5.name -> queryParameters._5.tsm(e),
            queryParameters._6.name -> queryParameters._6.tsm(f),
            queryParameters._7.name -> queryParameters._7.tsm(g),
            queryParameters._8.name -> queryParameters._8.tsm(h),
            queryParameters._9.name -> queryParameters._9.tsm(i)
          )
        )
      }
    }

  implicit def provideQueryParams10[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j)
          )
        )
      }
    }

  implicit def provideQueryParams11[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k)
          )
        )
      }
    }

  implicit def provideQueryParams12[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l)
          )
        )
      }
    }

  implicit def provideQueryParams13[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m)
          )
        )
      }
    }

  implicit def provideQueryParams14[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n)
          )
        )
      }
    }

  implicit def provideQueryParams15[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o)
          )
        )
      }
    }

  implicit def provideQueryParams16[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p)
          )
        )
      }
    }

  implicit def provideQueryParams17[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      T17,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16,
      U17 <: T17
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16],
        QueryParam[T17]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16],
          QueryParam[T17]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16],
              QueryParam[T17]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p),
            queryParameters._17.name -> queryParameters._17.tsm(q)
          )
        )
      }
    }

  implicit def provideQueryParams18[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      T17,
      T18,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16,
      U17 <: T17,
      U18 <: T18
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16],
        QueryParam[T17],
        QueryParam[T18]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16],
          QueryParam[T17],
          QueryParam[T18]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16],
              QueryParam[T17],
              QueryParam[T18]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p),
            queryParameters._17.name -> queryParameters._17.tsm(q),
            queryParameters._18.name -> queryParameters._18.tsm(r)
          )
        )
      }
    }

  implicit def provideQueryParams19[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      T17,
      T18,
      T19,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16,
      U17 <: T17,
      U18 <: T18,
      U19 <: T19
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16],
        QueryParam[T17],
        QueryParam[T18],
        QueryParam[T19]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16],
          QueryParam[T17],
          QueryParam[T18],
          QueryParam[T19]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16],
              QueryParam[T17],
              QueryParam[T18],
              QueryParam[T19]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p),
            queryParameters._17.name -> queryParameters._17.tsm(q),
            queryParameters._18.name -> queryParameters._18.tsm(r),
            queryParameters._19.name -> queryParameters._19.tsm(s)
          )
        )
      }
    }

  implicit def provideQueryParams20[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      T17,
      T18,
      T19,
      T20,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16,
      U17 <: T17,
      U18 <: T18,
      U19 <: T19,
      U20 <: T20
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16],
        QueryParam[T17],
        QueryParam[T18],
        QueryParam[T19],
        QueryParam[T20]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16],
          QueryParam[T17],
          QueryParam[T18],
          QueryParam[T19],
          QueryParam[T20]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16],
              QueryParam[T17],
              QueryParam[T18],
              QueryParam[T19],
              QueryParam[T20]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p),
            queryParameters._17.name -> queryParameters._17.tsm(q),
            queryParameters._18.name -> queryParameters._18.tsm(r),
            queryParameters._19.name -> queryParameters._19.tsm(s),
            queryParameters._20.name -> queryParameters._20.tsm(t)
          )
        )
      }
    }

  implicit def provideQueryParams21[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      T17,
      T18,
      T19,
      T20,
      T21,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16,
      U17 <: T17,
      U18 <: T18,
      U19 <: T19,
      U20 <: T20,
      U21 <: T21
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16],
        QueryParam[T17],
        QueryParam[T18],
        QueryParam[T19],
        QueryParam[T20],
        QueryParam[T21]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16],
          QueryParam[T17],
          QueryParam[T18],
          QueryParam[T19],
          QueryParam[T20],
          QueryParam[T21]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16],
              QueryParam[T17],
              QueryParam[T18],
              QueryParam[T19],
              QueryParam[T20],
              QueryParam[T21]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p),
            queryParameters._17.name -> queryParameters._17.tsm(q),
            queryParameters._18.name -> queryParameters._18.tsm(r),
            queryParameters._19.name -> queryParameters._19.tsm(s),
            queryParameters._20.name -> queryParameters._20.tsm(t),
            queryParameters._21.name -> queryParameters._21.tsm(u)
          )
        )
      }
    }

  implicit def provideQueryParams22[
      T1,
      T2,
      T3,
      T4,
      T5,
      T6,
      T7,
      T8,
      T9,
      T10,
      T11,
      T12,
      T13,
      T14,
      T15,
      T16,
      T17,
      T18,
      T19,
      T20,
      T21,
      T22,
      U1 <: T1,
      U2 <: T2,
      U3 <: T3,
      U4 <: T4,
      U5 <: T5,
      U6 <: T6,
      U7 <: T7,
      U8 <: T8,
      U9 <: T9,
      U10 <: T10,
      U11 <: T11,
      U12 <: T12,
      U13 <: T13,
      U14 <: T14,
      U15 <: T15,
      U16 <: T16,
      U17 <: T17,
      U18 <: T18,
      U19 <: T19,
      U20 <: T20,
      U21 <: T21,
      U22 <: T22
  ]: ProvideQueryParams[
    (
        QueryParam[T1],
        QueryParam[T2],
        QueryParam[T3],
        QueryParam[T4],
        QueryParam[T5],
        QueryParam[T6],
        QueryParam[T7],
        QueryParam[T8],
        QueryParam[T9],
        QueryParam[T10],
        QueryParam[T11],
        QueryParam[T12],
        QueryParam[T13],
        QueryParam[T14],
        QueryParam[T15],
        QueryParam[T16],
        QueryParam[T17],
        QueryParam[T18],
        QueryParam[T19],
        QueryParam[T20],
        QueryParam[T21],
        QueryParam[T22]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
  ] =
    new ProvideQueryParams[
      (
          QueryParam[T1],
          QueryParam[T2],
          QueryParam[T3],
          QueryParam[T4],
          QueryParam[T5],
          QueryParam[T6],
          QueryParam[T7],
          QueryParam[T8],
          QueryParam[T9],
          QueryParam[T10],
          QueryParam[T11],
          QueryParam[T12],
          QueryParam[T13],
          QueryParam[T14],
          QueryParam[T15],
          QueryParam[T16],
          QueryParam[T17],
          QueryParam[T18],
          QueryParam[T19],
          QueryParam[T20],
          QueryParam[T21],
          QueryParam[T22]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
    ] {
      override def apply(
          queryParameters: (
              QueryParam[T1],
              QueryParam[T2],
              QueryParam[T3],
              QueryParam[T4],
              QueryParam[T5],
              QueryParam[T6],
              QueryParam[T7],
              QueryParam[T8],
              QueryParam[T9],
              QueryParam[T10],
              QueryParam[T11],
              QueryParam[T12],
              QueryParam[T13],
              QueryParam[T14],
              QueryParam[T15],
              QueryParam[T16],
              QueryParam[T17],
              QueryParam[T18],
              QueryParam[T19],
              QueryParam[T20],
              QueryParam[T21],
              QueryParam[T22]
          ),
          queryParametersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22),
          uri: String
      ): String = {
        val (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v) = queryParametersProvided
        addQueryParametersToUri(
          uri,
          Map(
            queryParameters._1.name  -> queryParameters._1.tsm(a),
            queryParameters._2.name  -> queryParameters._2.tsm(b),
            queryParameters._3.name  -> queryParameters._3.tsm(c),
            queryParameters._4.name  -> queryParameters._4.tsm(d),
            queryParameters._5.name  -> queryParameters._5.tsm(e),
            queryParameters._6.name  -> queryParameters._6.tsm(f),
            queryParameters._7.name  -> queryParameters._7.tsm(g),
            queryParameters._8.name  -> queryParameters._8.tsm(h),
            queryParameters._9.name  -> queryParameters._9.tsm(i),
            queryParameters._10.name -> queryParameters._10.tsm(j),
            queryParameters._11.name -> queryParameters._11.tsm(k),
            queryParameters._12.name -> queryParameters._12.tsm(l),
            queryParameters._13.name -> queryParameters._13.tsm(m),
            queryParameters._14.name -> queryParameters._14.tsm(n),
            queryParameters._15.name -> queryParameters._15.tsm(o),
            queryParameters._16.name -> queryParameters._16.tsm(p),
            queryParameters._17.name -> queryParameters._17.tsm(q),
            queryParameters._18.name -> queryParameters._18.tsm(r),
            queryParameters._19.name -> queryParameters._19.tsm(s),
            queryParameters._20.name -> queryParameters._20.tsm(t),
            queryParameters._21.name -> queryParameters._21.tsm(u),
            queryParameters._22.name -> queryParameters._22.tsm(v)
          )
        )
      }
    }

  // TODO: created by chatgpt, check later
  def addQueryParametersToUri(uri: String, queryParameters: Map[String, Seq[String]]): String = {
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
}
