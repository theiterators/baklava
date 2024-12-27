package pl.iterators.kebs.baklava.params.enums

import pl.iterators.baklava.{ToPathParam, ToQueryParam}
import pl.iterators.kebs.core.enums.{EnumLike, ValueEnumLike, ValueEnumLikeEntry}

trait KebsBaklavaEnumsParams {
  implicit def toQueryParamEnum[T](implicit _enum: EnumLike[T]): ToQueryParam[T] = new ToQueryParam[T] {
    override def apply(t: T): Seq[String] = {
      val _ = _enum // fix warning
      Seq(t.toString)
    }
  }

  implicit def toPathParamEnum[T](implicit _enum: EnumLike[T]): ToPathParam[T] = new ToPathParam[T] {
    override def apply(t: T): String = {
      val _ = _enum // fix warning
      t.toString
    }
  }

  trait KebsBaklavaEnumsUppercaseParams {
    implicit def toQueryParamEnum[T](implicit _enum: EnumLike[T]): ToQueryParam[T] = new ToQueryParam[T] {
      override def apply(t: T): Seq[String] = {
        val _ = _enum // fix warning
        Seq(t.toString.toUpperCase)
      }
    }

    implicit def toPathParamEnum[T](implicit _enum: EnumLike[T]): ToPathParam[T] = new ToPathParam[T] {
      override def apply(t: T): String = {
        val _ = _enum // fix warning
        t.toString.toUpperCase
      }
    }
  }

  trait KebsBaklavaEnumsLowercaseParams {
    implicit def toQueryParamEnum[T](implicit _enum: EnumLike[T]): ToQueryParam[T] = new ToQueryParam[T] {
      override def apply(t: T): Seq[String] = {
        val _ = _enum // fix warning
        Seq(t.toString.toLowerCase)
      }
    }

    implicit def toPathParamEnum[T](implicit _enum: EnumLike[T]): ToPathParam[T] = new ToPathParam[T] {
      override def apply(t: T): String = {
        val _ = _enum // fix warning
        t.toString.toLowerCase
      }
    }
  }
}

trait KebsBaklavaValueEnumsParams {
  implicit def toQueryParamValueEnum[T, V <: ValueEnumLikeEntry[T]](implicit
      valueEnumLike: ValueEnumLike[T, V],
      tsm: ToQueryParam[V]
  ): ToQueryParam[T] = new ToQueryParam[T] {
    override def apply(t: T): Seq[String] = tsm(valueEnumLike.valueOf(t))
  }
}
