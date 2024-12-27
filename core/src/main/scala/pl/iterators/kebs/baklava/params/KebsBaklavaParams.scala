package pl.iterators.kebs.baklava.params

import pl.iterators.baklava.*
import pl.iterators.kebs.core.macros.ValueClassLike

trait KebsBaklavaParams {
  implicit def toQueryParamValueClassLike[T, U](implicit ev: ValueClassLike[T, U], tsm: ToQueryParam[U]): ToQueryParam[T] =
    new ToQueryParam[T] {
      override def apply(t: T): Seq[String] = tsm(ev.unapply(t))
    }

  implicit def toPathParamValueClassLike[T, U](implicit ev: ValueClassLike[T, U], tsm: ToPathParam[U]): ToPathParam[T] =
    new ToPathParam[T] {
      override def apply(t: T): String = tsm(ev.unapply(t))
    }
}
