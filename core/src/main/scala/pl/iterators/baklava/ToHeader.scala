package pl.iterators.baklava

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
