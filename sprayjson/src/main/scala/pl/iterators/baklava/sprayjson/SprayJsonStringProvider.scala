package pl.iterators.baklava.sprayjson

import pl.iterators.baklava.core.model.JsonStringPrinter
import spray.json._

trait SprayJsonStringProvider {

  implicit val unitPrinter: JsonStringPrinter[Unit] = new JsonStringPrinter[Unit] {
    override def printJson(obj: Unit): String = ""
  }

  implicit def sprayJsonString[T](implicit jsonWriter: JsonWriter[T]): JsonStringPrinter[T] = (obj: T) => obj.toJson(jsonWriter).prettyPrint

}
