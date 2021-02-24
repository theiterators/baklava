package pl.iterators.baklava.core.model

import scala.reflect.runtime.universe.TypeTag

case class RouteParameterRepresentation[T](
    name: String,
    required: Boolean,
    sampleValue: T,
    marshaller: T => String
)(implicit typeTag: TypeTag[T]) {

  lazy val marshall: String = marshaller(sampleValue)

  lazy val scalaType: String = typeTag.tpe.toString
}
