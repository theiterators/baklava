package pl.iterators.baklava.circe

import io.circe.Encoder
import pl.iterators.baklava.core.model.JsonStringPrinter

trait CirceJsonStringProvider {

  implicit val unitPrinter = new JsonStringPrinter[Unit] {
    override def printJson(obj: Unit): String = ""
  }

  implicit def circeJsonString[T](implicit encoder: Encoder[T]) = new JsonStringPrinter[T] {
    override def printJson(obj: T): String = encoder.apply(obj).toString()
  }

}
