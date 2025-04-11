package pl.iterators.baklava

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

trait ToPathParam[T] {
  def apply(t: T): String
}

case class PathParam[T](name: String, description: Option[String])(implicit val tsm: ToPathParam[T], val schema: Schema[T]) {
  type Underlying = T
}

trait ToPathParamSeq[T] {
  def apply(t: T): Seq[PathParam[?]]
}

trait ProvidePathParams[T, U] {
  def apply(
      pathParams: T,
      pathParamsProvided: U,
      uri: String
  ): String
}

trait BaklavaPathParams {
  def p[T](name: String, description: String = "")(implicit tsm: ToPathParam[T], schema: Schema[T]): PathParam[T] =
    PathParam[T](name, if (description.trim.isEmpty) None else Some(description.trim))

  implicit val stringToPathParam: ToPathParam[String] = new ToPathParam[String] {
    override def apply(t: String): String = t
  }

  implicit val intToPathParam: ToPathParam[Int] = new ToPathParam[Int] {
    override def apply(t: Int): String = t.toString
  }

  implicit val longToPathParam: ToPathParam[Long] = new ToPathParam[Long] {
    override def apply(t: Long): String = t.toString
  }

  implicit val doubleToPathParam: ToPathParam[Double] = new ToPathParam[Double] {
    override def apply(t: Double): String = t.toString
  }

  implicit val floatToPathParam: ToPathParam[Float] = new ToPathParam[Float] {
    override def apply(t: Float): String = t.toString
  }

  implicit val booleanToPathParam: ToPathParam[Boolean] = new ToPathParam[Boolean] {
    override def apply(t: Boolean): String = t.toString
  }

  implicit val byteToPathParam: ToPathParam[Byte] = new ToPathParam[Byte] {
    override def apply(t: Byte): String = t.toString
  }

  implicit val shortToPathParam: ToPathParam[Short] = new ToPathParam[Short] {
    override def apply(t: Short): String = t.toString
  }

  implicit val charToPathParam: ToPathParam[Char] = new ToPathParam[Char] {
    override def apply(t: Char): String = t.toString
  }

  implicit val bigDecimalToPathParam: ToPathParam[BigDecimal] = new ToPathParam[BigDecimal] {
    override def apply(t: BigDecimal): String = t.toString
  }

  implicit val uuidToPathParam: ToPathParam[java.util.UUID] = new ToPathParam[java.util.UUID] {
    override def apply(t: java.util.UUID): String = t.toString
  }

  implicit val unitToPathParamSeq: ToPathParamSeq[Unit] = new ToPathParamSeq[Unit] {
    override def apply(t: Unit): Seq[PathParam[?]] = Seq.empty
  }

  implicit def singleValueToPathParamSeq[T]: ToPathParamSeq[PathParam[T]] = new ToPathParamSeq[PathParam[T]] {
    override def apply(t: PathParam[T]): Seq[PathParam[?]] = Seq(t)
  }

  implicit def tuple2ToPathParamSeq[T1, T2]: ToPathParamSeq[(PathParam[T1], PathParam[T2])] =
    new ToPathParamSeq[(PathParam[T1], PathParam[T2])] {
      override def apply(t: (PathParam[T1], PathParam[T2])): Seq[PathParam[?]] = Seq(t._1, t._2)
    }

  implicit def tuple3ToPathParamSeq[T1, T2, T3]: ToPathParamSeq[(PathParam[T1], PathParam[T2], PathParam[T3])] =
    new ToPathParamSeq[(PathParam[T1], PathParam[T2], PathParam[T3])] {
      override def apply(t: (PathParam[T1], PathParam[T2], PathParam[T3])): Seq[PathParam[?]] = Seq(t._1, t._2, t._3)
    }

  implicit def tuple4ToPathParamSeq[T1, T2, T3, T4]: ToPathParamSeq[(PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4])] =
    new ToPathParamSeq[(PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4])] {
      override def apply(t: (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4])): Seq[PathParam[?]] = Seq(t._1, t._2, t._3, t._4)
    }

  // TODO: more tuples

  implicit val providePathParamsByUnit: ProvidePathParams[Unit, Unit] = new ProvidePathParams[Unit, Unit] {
    override def apply(
        pathParams: Unit,
        pathParamsProvided: Unit,
        uri: String
    ): String = uri
  }

  // TODO: it's ugly
  implicit def providePathParamsSingleValue[T, U <: T]: ProvidePathParams[PathParam[T], U] = new ProvidePathParams[PathParam[T], U] {
    override def apply(
        pathParams: PathParam[T],
        pathParamsProvided: U,
        uri: String
    ): String =
      uri.replace(s"{${pathParams.name}}", URLEncoder.encode(pathParams.tsm(pathParamsProvided), StandardCharsets.UTF_8.toString))
  }

  implicit def providePathParams2[T1, T2, U1 <: T1, U2 <: T2]: ProvidePathParams[(PathParam[T1], PathParam[T2]), (U1, U2)] =
    new ProvidePathParams[(PathParam[T1], PathParam[T2]), (U1, U2)] {
      override def apply(
          pathParams: (PathParam[T1], PathParam[T2]),
          pathParamsProvided: (U1, U2),
          uri: String
      ): String = {
        val (param1, param2)       = pathParams
        val (provided1, provided2) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams3[T1, T2, T3, U1 <: T1, U2 <: T2, U3 <: T3]
      : ProvidePathParams[(PathParam[T1], PathParam[T2], PathParam[T3]), (U1, U2, U3)] =
    new ProvidePathParams[(PathParam[T1], PathParam[T2], PathParam[T3]), (U1, U2, U3)] {
      override def apply(
          pathParams: (PathParam[T1], PathParam[T2], PathParam[T3]),
          pathParamsProvided: (U1, U2, U3),
          uri: String
      ): String = {
        val (param1, param2, param3)          = pathParams
        val (provided1, provided2, provided3) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams4[T1, T2, T3, T4, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4]
      : ProvidePathParams[(PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4]), (U1, U2, U3, U4)] =
    new ProvidePathParams[(PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4]), (U1, U2, U3, U4)] {
      override def apply(
          pathParams: (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4]),
          pathParamsProvided: (U1, U2, U3, U4),
          uri: String
      ): String = {
        val (param1, param2, param3, param4)             = pathParams
        val (provided1, provided2, provided3, provided4) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams5[T1, T2, T3, T4, T5, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5]
      : ProvidePathParams[(PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5]), (U1, U2, U3, U4, U5)] =
    new ProvidePathParams[(PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5]), (U1, U2, U3, U4, U5)] {
      override def apply(
          pathParams: (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5]),
          pathParamsProvided: (U1, U2, U3, U4, U5),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5)                = pathParams
        val (provided1, provided2, provided3, provided4, provided5) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4 = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams6[T1, T2, T3, T4, T5, T6, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5, U6 <: T6]: ProvidePathParams[
    (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6]),
    (U1, U2, U3, U4, U5, U6)
  ] =
    new ProvidePathParams[
      (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6]),
      (U1, U2, U3, U4, U5, U6)
    ] {
      override def apply(
          pathParams: (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6]),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6)                   = pathParams
        val (provided1, provided2, provided3, provided4, provided5, provided6) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4 = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5 = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams7[T1, T2, T3, T4, T5, T6, T7, U1 <: T1, U2 <: T2, U3 <: T3, U4 <: T4, U5 <: T5, U6 <: T6, U7 <: T7]
      : ProvidePathParams[
        (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6], PathParam[T7]),
        (U1, U2, U3, U4, U5, U6, U7)
      ] =
    new ProvidePathParams[
      (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6], PathParam[T7]),
      (U1, U2, U3, U4, U5, U6, U7)
    ] {
      override def apply(
          pathParams: (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6], PathParam[T7]),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7)                      = pathParams
        val (provided1, provided2, provided3, provided4, provided5, provided6, provided7) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4 = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5 = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6 = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams8[
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
  ]: ProvidePathParams[
    (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6], PathParam[T7], PathParam[T8]),
    (U1, U2, U3, U4, U5, U6, U7, U8)
  ] =
    new ProvidePathParams[
      (PathParam[T1], PathParam[T2], PathParam[T3], PathParam[T4], PathParam[T5], PathParam[T6], PathParam[T7], PathParam[T8]),
      (U1, U2, U3, U4, U5, U6, U7, U8)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8)                         = pathParams
        val (provided1, provided2, provided3, provided4, provided5, provided6, provided7, provided8) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4 = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5 = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6 = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7 = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams9[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8, param9)                            = pathParams
        val (provided1, provided2, provided3, provided4, provided5, provided6, provided7, provided8, provided9) = pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4 = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5 = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6 = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7 = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8 = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams10[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8, param9, param10) = pathParams
        val (provided1, provided2, provided3, provided4, provided5, provided6, provided7, provided8, provided9, provided10) =
          pathParamsProvided
        val uri1 = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2 = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3 = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4 = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5 = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6 = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7 = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8 = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9 = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams11[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8, param9, param10, param11) = pathParams
        val (provided1, provided2, provided3, provided4, provided5, provided6, provided7, provided8, provided9, provided10, provided11) =
          pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams12[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8, param9, param10, param11, param12) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams13[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8, param9, param10, param11, param12, param13) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams14[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14),
          uri: String
      ): String = {
        val (param1, param2, param3, param4, param5, param6, param7, param8, param9, param10, param11, param12, param13, param14) =
          pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams15[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams16[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams17[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16],
        PathParam[T17]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16],
          PathParam[T17]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16],
              PathParam[T17]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16,
          param17
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16,
          provided17
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        val uri16 = uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
        uri16.replace(s"{${param17.name}}", URLEncoder.encode(param17.tsm(provided17), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams18[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16],
        PathParam[T17],
        PathParam[T18]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16],
          PathParam[T17],
          PathParam[T18]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16],
              PathParam[T17],
              PathParam[T18]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16,
          param17,
          param18
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16,
          provided17,
          provided18
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        val uri16 = uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
        val uri17 = uri16.replace(s"{${param17.name}}", URLEncoder.encode(param17.tsm(provided17), StandardCharsets.UTF_8.toString))
        uri17.replace(s"{${param18.name}}", URLEncoder.encode(param18.tsm(provided18), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams19[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16],
        PathParam[T17],
        PathParam[T18],
        PathParam[T19]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16],
          PathParam[T17],
          PathParam[T18],
          PathParam[T19]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16],
              PathParam[T17],
              PathParam[T18],
              PathParam[T19]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16,
          param17,
          param18,
          param19
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16,
          provided17,
          provided18,
          provided19
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        val uri16 = uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
        val uri17 = uri16.replace(s"{${param17.name}}", URLEncoder.encode(param17.tsm(provided17), StandardCharsets.UTF_8.toString))
        val uri18 = uri17.replace(s"{${param18.name}}", URLEncoder.encode(param18.tsm(provided18), StandardCharsets.UTF_8.toString))
        uri18.replace(s"{${param19.name}}", URLEncoder.encode(param19.tsm(provided19), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams20[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16],
        PathParam[T17],
        PathParam[T18],
        PathParam[T19],
        PathParam[T20]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16],
          PathParam[T17],
          PathParam[T18],
          PathParam[T19],
          PathParam[T20]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16],
              PathParam[T17],
              PathParam[T18],
              PathParam[T19],
              PathParam[T20]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16,
          param17,
          param18,
          param19,
          param20
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16,
          provided17,
          provided18,
          provided19,
          provided20
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        val uri16 = uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
        val uri17 = uri16.replace(s"{${param17.name}}", URLEncoder.encode(param17.tsm(provided17), StandardCharsets.UTF_8.toString))
        val uri18 = uri17.replace(s"{${param18.name}}", URLEncoder.encode(param18.tsm(provided18), StandardCharsets.UTF_8.toString))
        val uri19 = uri18.replace(s"{${param19.name}}", URLEncoder.encode(param19.tsm(provided19), StandardCharsets.UTF_8.toString))
        uri19.replace(s"{${param20.name}}", URLEncoder.encode(param20.tsm(provided20), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams21[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16],
        PathParam[T17],
        PathParam[T18],
        PathParam[T19],
        PathParam[T20],
        PathParam[T21]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16],
          PathParam[T17],
          PathParam[T18],
          PathParam[T19],
          PathParam[T20],
          PathParam[T21]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16],
              PathParam[T17],
              PathParam[T18],
              PathParam[T19],
              PathParam[T20],
              PathParam[T21]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16,
          param17,
          param18,
          param19,
          param20,
          param21
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16,
          provided17,
          provided18,
          provided19,
          provided20,
          provided21
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        val uri16 = uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
        val uri17 = uri16.replace(s"{${param17.name}}", URLEncoder.encode(param17.tsm(provided17), StandardCharsets.UTF_8.toString))
        val uri18 = uri17.replace(s"{${param18.name}}", URLEncoder.encode(param18.tsm(provided18), StandardCharsets.UTF_8.toString))
        val uri19 = uri18.replace(s"{${param19.name}}", URLEncoder.encode(param19.tsm(provided19), StandardCharsets.UTF_8.toString))
        val uri20 = uri19.replace(s"{${param20.name}}", URLEncoder.encode(param20.tsm(provided20), StandardCharsets.UTF_8.toString))
        uri20.replace(s"{${param21.name}}", URLEncoder.encode(param21.tsm(provided21), StandardCharsets.UTF_8.toString))
      }
    }

  implicit def providePathParams22[
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
  ]: ProvidePathParams[
    (
        PathParam[T1],
        PathParam[T2],
        PathParam[T3],
        PathParam[T4],
        PathParam[T5],
        PathParam[T6],
        PathParam[T7],
        PathParam[T8],
        PathParam[T9],
        PathParam[T10],
        PathParam[T11],
        PathParam[T12],
        PathParam[T13],
        PathParam[T14],
        PathParam[T15],
        PathParam[T16],
        PathParam[T17],
        PathParam[T18],
        PathParam[T19],
        PathParam[T20],
        PathParam[T21],
        PathParam[T22]
    ),
    (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
  ] =
    new ProvidePathParams[
      (
          PathParam[T1],
          PathParam[T2],
          PathParam[T3],
          PathParam[T4],
          PathParam[T5],
          PathParam[T6],
          PathParam[T7],
          PathParam[T8],
          PathParam[T9],
          PathParam[T10],
          PathParam[T11],
          PathParam[T12],
          PathParam[T13],
          PathParam[T14],
          PathParam[T15],
          PathParam[T16],
          PathParam[T17],
          PathParam[T18],
          PathParam[T19],
          PathParam[T20],
          PathParam[T21],
          PathParam[T22]
      ),
      (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22)
    ] {
      override def apply(
          pathParams: (
              PathParam[T1],
              PathParam[T2],
              PathParam[T3],
              PathParam[T4],
              PathParam[T5],
              PathParam[T6],
              PathParam[T7],
              PathParam[T8],
              PathParam[T9],
              PathParam[T10],
              PathParam[T11],
              PathParam[T12],
              PathParam[T13],
              PathParam[T14],
              PathParam[T15],
              PathParam[T16],
              PathParam[T17],
              PathParam[T18],
              PathParam[T19],
              PathParam[T20],
              PathParam[T21],
              PathParam[T22]
          ),
          pathParamsProvided: (U1, U2, U3, U4, U5, U6, U7, U8, U9, U10, U11, U12, U13, U14, U15, U16, U17, U18, U19, U20, U21, U22),
          uri: String
      ): String = {
        val (
          param1,
          param2,
          param3,
          param4,
          param5,
          param6,
          param7,
          param8,
          param9,
          param10,
          param11,
          param12,
          param13,
          param14,
          param15,
          param16,
          param17,
          param18,
          param19,
          param20,
          param21,
          param22
        ) = pathParams
        val (
          provided1,
          provided2,
          provided3,
          provided4,
          provided5,
          provided6,
          provided7,
          provided8,
          provided9,
          provided10,
          provided11,
          provided12,
          provided13,
          provided14,
          provided15,
          provided16,
          provided17,
          provided18,
          provided19,
          provided20,
          provided21,
          provided22
        ) = pathParamsProvided
        val uri1  = uri.replace(s"{${param1.name}}", URLEncoder.encode(param1.tsm(provided1), StandardCharsets.UTF_8.toString))
        val uri2  = uri1.replace(s"{${param2.name}}", URLEncoder.encode(param2.tsm(provided2), StandardCharsets.UTF_8.toString))
        val uri3  = uri2.replace(s"{${param3.name}}", URLEncoder.encode(param3.tsm(provided3), StandardCharsets.UTF_8.toString))
        val uri4  = uri3.replace(s"{${param4.name}}", URLEncoder.encode(param4.tsm(provided4), StandardCharsets.UTF_8.toString))
        val uri5  = uri4.replace(s"{${param5.name}}", URLEncoder.encode(param5.tsm(provided5), StandardCharsets.UTF_8.toString))
        val uri6  = uri5.replace(s"{${param6.name}}", URLEncoder.encode(param6.tsm(provided6), StandardCharsets.UTF_8.toString))
        val uri7  = uri6.replace(s"{${param7.name}}", URLEncoder.encode(param7.tsm(provided7), StandardCharsets.UTF_8.toString))
        val uri8  = uri7.replace(s"{${param8.name}}", URLEncoder.encode(param8.tsm(provided8), StandardCharsets.UTF_8.toString))
        val uri9  = uri8.replace(s"{${param9.name}}", URLEncoder.encode(param9.tsm(provided9), StandardCharsets.UTF_8.toString))
        val uri10 = uri9.replace(s"{${param10.name}}", URLEncoder.encode(param10.tsm(provided10), StandardCharsets.UTF_8.toString))
        val uri11 = uri10.replace(s"{${param11.name}}", URLEncoder.encode(param11.tsm(provided11), StandardCharsets.UTF_8.toString))
        val uri12 = uri11.replace(s"{${param12.name}}", URLEncoder.encode(param12.tsm(provided12), StandardCharsets.UTF_8.toString))
        val uri13 = uri12.replace(s"{${param13.name}}", URLEncoder.encode(param13.tsm(provided13), StandardCharsets.UTF_8.toString))
        val uri14 = uri13.replace(s"{${param14.name}}", URLEncoder.encode(param14.tsm(provided14), StandardCharsets.UTF_8.toString))
        val uri15 = uri14.replace(s"{${param15.name}}", URLEncoder.encode(param15.tsm(provided15), StandardCharsets.UTF_8.toString))
        val uri16 = uri15.replace(s"{${param16.name}}", URLEncoder.encode(param16.tsm(provided16), StandardCharsets.UTF_8.toString))
        val uri17 = uri16.replace(s"{${param17.name}}", URLEncoder.encode(param17.tsm(provided17), StandardCharsets.UTF_8.toString))
        val uri18 = uri17.replace(s"{${param18.name}}", URLEncoder.encode(param18.tsm(provided18), StandardCharsets.UTF_8.toString))
        val uri19 = uri18.replace(s"{${param19.name}}", URLEncoder.encode(param19.tsm(provided19), StandardCharsets.UTF_8.toString))
        val uri20 = uri19.replace(s"{${param20.name}}", URLEncoder.encode(param20.tsm(provided20), StandardCharsets.UTF_8.toString))
        val uri21 = uri20.replace(s"{${param21.name}}", URLEncoder.encode(param21.tsm(provided21), StandardCharsets.UTF_8.toString))
        uri21.replace(s"{${param22.name}}", URLEncoder.encode(param22.tsm(provided22), StandardCharsets.UTF_8.toString))
      }
    }
}
