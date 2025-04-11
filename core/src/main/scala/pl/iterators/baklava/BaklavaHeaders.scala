package pl.iterators.baklava

import scala.util.Try

trait ToHeader[T] {
  def apply(value: T): Option[String]
  def unapply(value: String): Option[T]
}

case class Header[T](name: String, description: Option[String])(implicit val tsm: ToHeader[T], val schema: Schema[T]) {
  type Underlying = T
}

trait ToHeaderSeq[T] {
  def apply(t: T): Seq[Header[?]]
}

trait ProvideHeaders[T, U] {
  def apply(
      headers: T,
      headersProvided: U
  ): Map[String, String]
}

trait BaklavaHeaders {
  def h[T](name: String, description: String = "")(implicit tsm: ToHeader[T], schema: Schema[T]): Header[T] =
    Header[T](name, if (description.trim.isEmpty) None else Some(description.trim))

  implicit val stringToHeader: ToHeader[String] = new ToHeader[String] {
    override def apply(value: String): Option[String] = Some(value)

    override def unapply(value: String): Option[String] = Some(value)
  }

  implicit val intToHeader: ToHeader[Int] = new ToHeader[Int] {
    override def apply(value: Int): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Int] = Try(value.toInt).toOption
  }

  implicit val longToHeader: ToHeader[Long] = new ToHeader[Long] {
    override def apply(value: Long): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Long] = Try(value.toLong).toOption
  }

  implicit val doubleToHeader: ToHeader[Double] = new ToHeader[Double] {
    override def apply(value: Double): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Double] = Try(value.toDouble).toOption
  }

  implicit val floatToHeader: ToHeader[Float] = new ToHeader[Float] {
    override def apply(value: Float): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Float] = Try(value.toFloat).toOption
  }

  implicit val booleanToHeader: ToHeader[Boolean] = new ToHeader[Boolean] {
    override def apply(value: Boolean): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Boolean] = Try(value.toBoolean).toOption
  }

  implicit val byteToHeader: ToHeader[Byte] = new ToHeader[Byte] {
    override def apply(value: Byte): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Byte] = Try(value.toByte).toOption
  }

  implicit val shortToHeader: ToHeader[Short] = new ToHeader[Short] {
    override def apply(value: Short): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Short] = Try(value.toShort).toOption
  }

  implicit val charToHeader: ToHeader[Char] = new ToHeader[Char] {
    override def apply(value: Char): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[Char] = value.headOption
  }

  implicit val bigDecimalToHeader: ToHeader[BigDecimal] = new ToHeader[BigDecimal] {
    override def apply(value: BigDecimal): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[BigDecimal] = Try(BigDecimal(value)).toOption
  }

  implicit val uuidToHeader: ToHeader[java.util.UUID] = new ToHeader[java.util.UUID] {
    override def apply(value: java.util.UUID): Option[String] = Some(value.toString)

    override def unapply(value: String): Option[java.util.UUID] = Try(java.util.UUID.fromString(value)).toOption
  }

  implicit def optionToHeader[T](implicit tsm: ToHeader[T]): ToHeader[Option[T]] = new ToHeader[Option[T]] {
    override def apply(value: Option[T]): Option[String] = value.flatMap(tsm.apply)

    override def unapply(value: String): Option[Option[T]] = tsm.unapply(value).map(Some(_))
  }

  implicit val unitToHeaderSeq: ToHeaderSeq[Unit] = new ToHeaderSeq[Unit] {
    override def apply(t: Unit): Seq[Header[?]] = Seq.empty
  }

  implicit def singleValueToHeaderSeq[T]: ToHeaderSeq[Header[T]] = new ToHeaderSeq[Header[T]] {
    override def apply(t: Header[T]): Seq[Header[?]] = Seq(t)
  }

  implicit def tuple2ToHeaderSeq[T1, T2]: ToHeaderSeq[(Header[T1], Header[T2])] =
    new ToHeaderSeq[(Header[T1], Header[T2])] {
      override def apply(t: (Header[T1], Header[T2])): Seq[Header[?]] = Seq(t._1, t._2)
    }

  implicit def tuple3ToHeaderSeq[T1, T2, T3]: ToHeaderSeq[(Header[T1], Header[T2], Header[T3])] =
    new ToHeaderSeq[(Header[T1], Header[T2], Header[T3])] {
      override def apply(t: (Header[T1], Header[T2], Header[T3])): Seq[Header[?]] = Seq(t._1, t._2, t._3)
    }

  implicit def tuple4ToHeaderSeq[T1, T2, T3, T4]: ToHeaderSeq[(Header[T1], Header[T2], Header[T3], Header[T4])] =
    new ToHeaderSeq[(Header[T1], Header[T2], Header[T3], Header[T4])] {
      override def apply(t: (Header[T1], Header[T2], Header[T3], Header[T4])): Seq[Header[?]] = Seq(t._1, t._2, t._3, t._4)
    }

  // TODO: more tuples

  implicit def providerHeadersByUnit[T]: ProvideHeaders[T, Unit] = new ProvideHeaders[T, Unit] {
    override def apply(headers: T, headersProvided: Unit): Map[String, String] = Map.empty
  }

  implicit def provideHeadersSingleValue[T, U <: T]: ProvideHeaders[Header[T], U] =
    new ProvideHeaders[Header[T], U] {
      override def apply(headers: Header[T], headersProvided: U): Map[String, String] =
        headers.tsm(headersProvided).map(headers.name -> _).toMap
    }

  implicit def provideHeaders2[T1, T2, U1 <: T1, U2 <: T2]: ProvideHeaders[(Header[T1], Header[T2]), (U1, U2)] =
    new ProvideHeaders[(Header[T1], Header[T2]), (U1, U2)] {
      override def apply(headers: (Header[T1], Header[T2]), headersProvided: (U1, U2)): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _)).toMap
    }

  implicit def provideHeaders3[T1, T2, T3, U1 <: T1, U2 <: T2, U3 <: T3]
      : ProvideHeaders[(Header[T1], Header[T2], Header[T3]), (U1, U2, U3)] =
    new ProvideHeaders[(Header[T1], Header[T2], Header[T3]), (U1, U2, U3)] {
      override def apply(headers: (Header[T1], Header[T2], Header[T3]), headersProvided: (U1, U2, U3)): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _)).toMap
    }

  implicit def provideHeaders4[T1, T2, T3, T4, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4]
      : ProvideHeaders[(Header[T1], Header[T2], Header[T3], Header[T4]), (U1, U2, U3, U4)] =
    new ProvideHeaders[(Header[T1], Header[T2], Header[T3], Header[T4]), (U1, U2, U3, U4)] {
      override def apply(
          headers: (Header[T1], Header[T2], Header[T3], Header[T4]),
          headersProvided: (U1, U2, U3, U4)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _)).toMap
    }

  implicit def provideHeaders5[T1, T2, T3, T4, T5, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5]
      : ProvideHeaders[(Header[T1], Header[T2], Header[T3], Header[T4], Header[T5]), (U1, U2, U3, U4, U5)] =
    new ProvideHeaders[(Header[T1], Header[T2], Header[T3], Header[T4], Header[T5]), (U1, U2, U3, U4, U5)] {
      override def apply(
          headers: (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5]),
          headersProvided: (U1, U2, U3, U4, U5)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _)).toMap
    }

  implicit def provideHeaders6[T1, T2, T3, T4, T5, T6, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5, U6 <: T6]
      : ProvideHeaders[(Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6]), (U1, U2, U3, U4, U5, U6)] =
    new ProvideHeaders[(Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6]), (U1, U2, U3, U4, U5, U6)] {
      override def apply(
          headers: (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6]),
          headersProvided: (U1, U2, U3, U4, U5, U6)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _)).toMap
    }

  implicit def provideHeaders7[T1, T2, T3, T4, T5, T6, T7, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5, U6 <: T6, U7 <: T7]
      : ProvideHeaders[
        (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7]),
        (U1, U2, U3, U4, U5, U6, U7)
      ] =
    new ProvideHeaders[
      (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7]),
      (U1, U2, U3, U4, U5, U6, U7)
    ] {
      override def apply(
          headers: (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7]),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _)).toMap
    }

  implicit def provideHeaders8[
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
  ]: ProvideHeaders[
    (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8]),
    (U1, U2, U3, U4, U5, U6, U7, U8)
  ] =
    new ProvideHeaders[
      (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8]),
      (U1, U2, U3, U4, U5, U6, U7, U8)
    ] {
      override def apply(
          headers: (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8]),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _)).toMap
    }

  implicit def provideHeaders9[
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
  ]: ProvideHeaders[
    (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8], Header[T9]),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9)
  ] =
    new ProvideHeaders[
      (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8], Header[T9]),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9)
    ] {
      override def apply(
          headers: (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8], Header[T9]),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _)).toMap
    }

  implicit def provideHeaders10[
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
  ]: ProvideHeaders[
    (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8], Header[T9], Header[T10]),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
  ] =
    new ProvideHeaders[
      (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8], Header[T9], Header[T10]),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _)).toMap
    }

  implicit def provideHeaders11[
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
  ]: ProvideHeaders[
    (Header[T1], Header[T2], Header[T3], Header[T4], Header[T5], Header[T6], Header[T7], Header[T8], Header[T9], Header[T10], Header[T11]),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _)).toMap
    }

  implicit def provideHeaders12[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _)).toMap
    }

  implicit def provideHeaders13[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _)).toMap
    }

  implicit def provideHeaders14[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _)).toMap
    }

  implicit def provideHeaders15[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _)).toMap
    }

  implicit def provideHeaders16[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _)).toMap
    }

  implicit def provideHeaders17[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16],
        Header[T17]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16],
          Header[T17]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16],
              Header[T17]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _) ++
          headers._17.tsm(headersProvided._17).map(headers._17.name -> _)).toMap
    }

  implicit def provideHeaders18[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16],
        Header[T17],
        Header[T18]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16],
          Header[T17],
          Header[T18]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16],
              Header[T17],
              Header[T18]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _) ++
          headers._17.tsm(headersProvided._17).map(headers._17.name -> _) ++
          headers._18.tsm(headersProvided._18).map(headers._18.name -> _)).toMap
    }

  implicit def provideHeaders19[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16],
        Header[T17],
        Header[T18],
        Header[T19]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16],
          Header[T17],
          Header[T18],
          Header[T19]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16],
              Header[T17],
              Header[T18],
              Header[T19]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _) ++
          headers._17.tsm(headersProvided._17).map(headers._17.name -> _) ++
          headers._18.tsm(headersProvided._18).map(headers._18.name -> _) ++
          headers._19.tsm(headersProvided._19).map(headers._19.name -> _)).toMap
    }

  implicit def provideHeaders20[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16],
        Header[T17],
        Header[T18],
        Header[T19],
        Header[T20]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16],
          Header[T17],
          Header[T18],
          Header[T19],
          Header[T20]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16],
              Header[T17],
              Header[T18],
              Header[T19],
              Header[T20]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _) ++
          headers._17.tsm(headersProvided._17).map(headers._17.name -> _) ++
          headers._18.tsm(headersProvided._18).map(headers._18.name -> _) ++
          headers._19.tsm(headersProvided._19).map(headers._19.name -> _) ++
          headers._20.tsm(headersProvided._20).map(headers._20.name -> _)).toMap
    }

  implicit def provideHeaders21[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16],
        Header[T17],
        Header[T18],
        Header[T19],
        Header[T20],
        Header[T21]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16],
          Header[T17],
          Header[T18],
          Header[T19],
          Header[T20],
          Header[T21]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16],
              Header[T17],
              Header[T18],
              Header[T19],
              Header[T20],
              Header[T21]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _) ++
          headers._17.tsm(headersProvided._17).map(headers._17.name -> _) ++
          headers._18.tsm(headersProvided._18).map(headers._18.name -> _) ++
          headers._19.tsm(headersProvided._19).map(headers._19.name -> _) ++
          headers._20.tsm(headersProvided._20).map(headers._20.name -> _) ++
          headers._21.tsm(headersProvided._21).map(headers._21.name -> _)).toMap
    }

  implicit def provideHeaders22[
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
  ]: ProvideHeaders[
    (
        Header[T1],
        Header[T2],
        Header[T3],
        Header[T4],
        Header[T5],
        Header[T6],
        Header[T7],
        Header[T8],
        Header[T9],
        Header[T10],
        Header[T11],
        Header[T12],
        Header[T13],
        Header[T14],
        Header[T15],
        Header[T16],
        Header[T17],
        Header[T18],
        Header[T19],
        Header[T20],
        Header[T21],
        Header[T22]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
  ] =
    new ProvideHeaders[
      (
          Header[T1],
          Header[T2],
          Header[T3],
          Header[T4],
          Header[T5],
          Header[T6],
          Header[T7],
          Header[T8],
          Header[T9],
          Header[T10],
          Header[T11],
          Header[T12],
          Header[T13],
          Header[T14],
          Header[T15],
          Header[T16],
          Header[T17],
          Header[T18],
          Header[T19],
          Header[T20],
          Header[T21],
          Header[T22]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
    ] {
      override def apply(
          headers: (
              Header[T1],
              Header[T2],
              Header[T3],
              Header[T4],
              Header[T5],
              Header[T6],
              Header[T7],
              Header[T8],
              Header[T9],
              Header[T10],
              Header[T11],
              Header[T12],
              Header[T13],
              Header[T14],
              Header[T15],
              Header[T16],
              Header[T17],
              Header[T18],
              Header[T19],
              Header[T20],
              Header[T21],
              Header[T22]
          ),
          headersProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
      ): Map[String, String] =
        (headers._1.tsm(headersProvided._1).map(headers._1.name -> _) ++
          headers._2.tsm(headersProvided._2).map(headers._2.name -> _) ++
          headers._3.tsm(headersProvided._3).map(headers._3.name -> _) ++
          headers._4.tsm(headersProvided._4).map(headers._4.name -> _) ++
          headers._5.tsm(headersProvided._5).map(headers._5.name -> _) ++
          headers._6.tsm(headersProvided._6).map(headers._6.name -> _) ++
          headers._7.tsm(headersProvided._7).map(headers._7.name -> _) ++
          headers._8.tsm(headersProvided._8).map(headers._8.name -> _) ++
          headers._9.tsm(headersProvided._9).map(headers._9.name -> _) ++
          headers._10.tsm(headersProvided._10).map(headers._10.name -> _) ++
          headers._11.tsm(headersProvided._11).map(headers._11.name -> _) ++
          headers._12.tsm(headersProvided._12).map(headers._12.name -> _) ++
          headers._13.tsm(headersProvided._13).map(headers._13.name -> _) ++
          headers._14.tsm(headersProvided._14).map(headers._14.name -> _) ++
          headers._15.tsm(headersProvided._15).map(headers._15.name -> _) ++
          headers._16.tsm(headersProvided._16).map(headers._16.name -> _) ++
          headers._17.tsm(headersProvided._17).map(headers._17.name -> _) ++
          headers._18.tsm(headersProvided._18).map(headers._18.name -> _) ++
          headers._19.tsm(headersProvided._19).map(headers._19.name -> _) ++
          headers._20.tsm(headersProvided._20).map(headers._20.name -> _) ++
          headers._21.tsm(headersProvided._21).map(headers._21.name -> _) ++
          headers._22.tsm(headersProvided._22).map(headers._22.name -> _)).toMap
    }

}
