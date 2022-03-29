package pl.iterators.baklava.sprayjson

import pl.iterators.baklava.core.model.JsonStringPrinter
import spray.json._

trait SprayJsonStringProvider {

  implicit val unitPrinter = new JsonStringPrinter[Unit] {
    override def printJson(obj: Unit): String = ""
  }

  implicit def sprayJsonString[T](implicit jsonWriter: JsonWriter[T]) = new JsonStringPrinter[T] {
    override def printJson(obj: T): String = obj.toJson(jsonWriter).prettyPrint
  }

}
