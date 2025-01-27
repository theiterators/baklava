package pl.iterators.baklava

trait ToHeader[T] {
  def apply(value: T): Option[String]
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
    }

    implicit val intToHeader: ToHeader[Int] = new ToHeader[Int] {
      override def apply(value: Int): Option[String] = Some(value.toString)
    }

    implicit val longToHeader: ToHeader[Long] = new ToHeader[Long] {
      override def apply(value: Long): Option[String] = Some(value.toString)
    }

    implicit val doubleToHeader: ToHeader[Double] = new ToHeader[Double] {
      override def apply(value: Double): Option[String] = Some(value.toString)
    }

    implicit val floatToHeader: ToHeader[Float] = new ToHeader[Float] {
      override def apply(value: Float): Option[String] = Some(value.toString)
    }

    implicit val booleanToHeader: ToHeader[Boolean] = new ToHeader[Boolean] {
      override def apply(value: Boolean): Option[String] = Some(value.toString)
    }

    implicit val byteToHeader: ToHeader[Byte] = new ToHeader[Byte] {
      override def apply(value: Byte): Option[String] = Some(value.toString)
    }

    implicit val shortToHeader: ToHeader[Short] = new ToHeader[Short] {
      override def apply(value: Short): Option[String] = Some(value.toString)
    }

    implicit val charToHeader: ToHeader[Char] = new ToHeader[Char] {
      override def apply(value: Char): Option[String] = Some(value.toString)
    }

    implicit val bigDecimalToHeader: ToHeader[BigDecimal] = new ToHeader[BigDecimal] {
      override def apply(value: BigDecimal): Option[String] = Some(value.toString)
    }

    implicit val uuidToHeader: ToHeader[java.util.UUID] = new ToHeader[java.util.UUID] {
      override def apply(value: java.util.UUID): Option[String] = Some(value.toString)
    }

    implicit def optionToHeader[T](implicit tsm: ToHeader[T]): ToHeader[Option[T]] = new ToHeader[Option[T]] {
      override def apply(value: Option[T]): Option[String] = value.flatMap(tsm.apply)
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
}
