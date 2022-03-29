package pl.iterators.baklava.core.model

import pl.iterators.kebs.jsonschema.JsonSchemaWrapper
import pl.iterators.kebs.scalacheck.AllGenerators
import pl.iterators.baklava.core.utils.option.RichOptionCompanion

import scala.reflect.runtime.universe._
import scala.util.Random

class RouteDtoHandler[T](implicit ttag: TypeTag[T],
                         generators: AllGenerators[T],
                         val jsonSchemaWrapper: JsonSchemaWrapper[T],
                         jsonPrinter: JsonStringPrinter[T]) {

  lazy val isUnit = ttag == implicitly[TypeTag[Unit]]

  lazy val scalaClassOpt: Option[String] =
    Option.when(!isUnit)(ttag.tpe.toString)
  lazy val schema: Option[json.Schema[T]] =
    Option.when(!isUnit)(jsonSchemaWrapper.schema)

  def minimal: RouteDtoValueWithJsonOpt[T] =
    RouteDtoValueWithJsonOpt[T](generators.minimal.generate)
  def normal: RouteDtoValueWithJsonOpt[T] =
    RouteDtoValueWithJsonOpt[T](generators.normal.generate)
  def maximal: RouteDtoValueWithJsonOpt[T] =
    RouteDtoValueWithJsonOpt[T](generators.maximal.generate)
  def get: RouteDtoValueWithJsonOpt[T] = normal
  def random: RouteDtoValueWithJsonOpt[T] = {
    val rnd = Random.nextInt(3)
    if (rnd == 0) minimal
    else if (rnd == 1) maximal
    else normal
  }
}

class RouteDtoHandlerWithPredefinedValue[T](value: T)(implicit ttag: TypeTag[T],
                                                      generators: AllGenerators[T],
                                                      override val jsonSchemaWrapper: JsonSchemaWrapper[T],
                                                      jsonPrinter: JsonStringPrinter[T])
    extends RouteDtoHandler {

  override lazy val normal: RouteDtoValueWithJsonOpt[T] =
    RouteDtoValueWithJsonOpt[T](value)
}

object RouteDtoHandler {
  def apply[T](predefinedValue: Option[T])(
      implicit ttag: TypeTag[T],
      generators: AllGenerators[T],
      jsonSchemaWrapper: JsonSchemaWrapper[T],
      jsonPrinter: JsonStringPrinter[T]
  ): RouteDtoHandler[T] =
    predefinedValue.fold(new RouteDtoHandler[T])(value => new RouteDtoHandlerWithPredefinedValue[T](value))
}

class RouteDtoValueWithJsonOpt[T] private (val value: T, val jsonString: Option[String]) {
  lazy val getJsonString: String = jsonString.getOrElse("")
}

object RouteDtoValueWithJsonOpt {

  def apply[T](value: T)(implicit ttag: TypeTag[T], jsonPrinter: JsonStringPrinter[T]): RouteDtoValueWithJsonOpt[T] = {
    new RouteDtoValueWithJsonOpt[T](
      value,
      Option.when(ttag != implicitly[TypeTag[Unit]])(jsonPrinter.printJson(value))
    )
  }

}

trait JsonStringPrinter[T] {
  def printJson(obj: T): String
}
