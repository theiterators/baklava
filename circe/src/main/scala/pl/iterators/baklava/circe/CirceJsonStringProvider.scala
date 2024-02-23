package pl.iterators.baklava.circe

import io.circe.Encoder
import pl.iterators.baklava.core.model.JsonStringPrinter

trait CirceJsonStringProvider {

  implicit val unitPrinter: JsonStringPrinter[Unit] = (obj: Unit) => ""

  implicit def circeJsonString[T](implicit encoder: Encoder[T]): JsonStringPrinter[T] = (obj: T) => encoder.apply(obj).toString()

}
