package pl.iterators.baklava.core.model

import scala.reflect.runtime.universe.TypeTag
import scala.util.Random

case class RouteParameterRepresentation[T](
  name: String,
  valueGenerator: () => T,
  marshaller: T => String = RouteParameterRepresentation.marshaller[T] _,
  required: Boolean = false,
  seq: Boolean = false,
  seqMin: Int = 2,
  seqMax: Int = 5,
  enumValues: Option[Seq[T]] = None
)(implicit typeTag: TypeTag[T]) {

  lazy val sampleValue: T =
    if (!seq) valueGenerator() else sys.error("sampleValue should not be called on seq parameter")

  lazy val seqSampleValue: Seq[T] =
    if (seq) (0 until Random.nextInt(1 + seqMax - seqMin) + seqMin).map(_ => valueGenerator())
    else sys.error("seqSampleValue should not be called on seq parameter")

  lazy val enums: Option[Seq[String]] = enumValues.map(values => values.map(marshaller))

  lazy val simpleType: String = typeTag.tpe.toString

  lazy val queryString: String =
    if (seq) {
      seqSampleValue.map(v => s"$name=${marshaller(v)}").mkString("&")
    } else
      s"$name=${marshaller(sampleValue)}"

}

object RouteParameterRepresentation {
  def marshaller[T](value: T): String =
    value match {
      case string: String => string
      case other          => other.toString
    }
}
