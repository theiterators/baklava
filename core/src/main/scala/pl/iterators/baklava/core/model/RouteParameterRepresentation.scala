package pl.iterators.baklava.core.model

import scala.reflect.runtime.universe.TypeTag

case class RouteParameterRepresentation[T](
    name: String,
    required: Boolean,
    sampleValue: T,
    marshaller: T => String,
    enumValues: Option[Seq[T]] = None
)(implicit typeTag: TypeTag[T]) {

  lazy val marshall: String = marshaller(sampleValue)

  lazy val enums: Option[Seq[String]] = enumValues.map(values => values.map(marshaller))

  lazy val scalaType: String = typeTag.tpe.toString
}
