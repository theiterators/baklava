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

  implicit val unitToQueryParamSeq: ToQueryParamSeq[Unit] = new ToQueryParamSeq[Unit] {
    override def apply(t: Unit): Seq[QueryParam[?]] = Seq.empty
  }

  implicit def singleValueToQueryParamSeq[T]: ToQueryParamSeq[QueryParam[T]] = new ToQueryParamSeq[QueryParam[T]] {
    override def apply(t: QueryParam[T]): Seq[QueryParam[?]] = Seq(t)
  }

  implicit def tuple2ToQueryParamSeq[A, B]: ToQueryParamSeq[(QueryParam[A], QueryParam[B])] =
    new ToQueryParamSeq[(QueryParam[A], QueryParam[B])] {
      override def apply(t: (QueryParam[A], QueryParam[B])): Seq[QueryParam[?]] = Seq(t._1, t._2)
    }

  implicit def tuple3ToQueryParamSeq[A, B, C]: ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C])] =
    new ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C])] {
      override def apply(t: (QueryParam[A], QueryParam[B], QueryParam[C])): Seq[QueryParam[?]] = Seq(t._1, t._2, t._3)
    }

  implicit def tuple4ToQueryParamSeq[A, B, C, D]: ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D])] =
    new ToQueryParamSeq[(QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D])] {
      override def apply(t: (QueryParam[A], QueryParam[B], QueryParam[C], QueryParam[D])): Seq[QueryParam[?]] =
        Seq(t._1, t._2, t._3, t._4)
    }

  // TODO: more tuples

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

  // TODO: more tuples

  // TODO: created by chatgpt, check later
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
}
