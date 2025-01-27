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

  // TODO: more tuples
}
