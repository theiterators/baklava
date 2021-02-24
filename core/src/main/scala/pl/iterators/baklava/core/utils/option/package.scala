package pl.iterators.baklava.core.utils

package object option {
  implicit class RichOptionCompanion(val self: Option.type) extends AnyVal {
    def when[A](cond: Boolean)(value: => A): Option[A] =
      if (cond) Some(value) else None
  }
}
