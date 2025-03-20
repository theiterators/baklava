package pl.iterators.baklava.core.model

import org.scalacheck.{Arbitrary, Gen}
import pl.iterators.kebs.jsonschema.JsonSchemaWrapper
import pl.iterators.baklava.core.utils.option.RichOptionCompanion
import pl.iterators.kebs.scalacheck._

import scala.reflect.runtime.universe._

class RouteDtoHandler[T](
  implicit ttag: TypeTag[T],
  arbitrary: Arbitrary[T],
  genParameters: Gen.Parameters,
  val jsonSchemaWrapper: JsonSchemaWrapper[T],
  jsonPrinter: JsonStringPrinter[T]) {

  lazy val isUnit = ttag == implicitly[TypeTag[Unit]]

  lazy val scalaClassOpt: Option[String] =
    Option.when(!isUnit)(ttag.tpe.toString)
  lazy val schema: Option[json.Schema[T]] =
    Option.when(!isUnit)(jsonSchemaWrapper.schema)

  def random: RouteDtoValueWithJsonOpt[T] =
    RouteDtoValueWithJsonOpt[T](generate[T]()(arbitrary, genParameters))
}

class RouteDtoHandlerWithPredefinedValue[T](
  value: T
)(implicit
  ttag: TypeTag[T],
  arbitrary: Arbitrary[T],
  genParameters: Gen.Parameters,
  override val jsonSchemaWrapper: JsonSchemaWrapper[T],
  jsonPrinter: JsonStringPrinter[T])
    extends RouteDtoHandler {

  override lazy val random: RouteDtoValueWithJsonOpt[T] =
    RouteDtoValueWithJsonOpt[T](value)
}

object RouteDtoHandler {
  def apply[T](
    predefinedValue: Option[T]
  )(implicit
    ttag: TypeTag[T],
    arbitrary: Arbitrary[T],
    genParameters: Gen.Parameters,
    jsonSchemaWrapper: JsonSchemaWrapper[T],
    jsonPrinter: JsonStringPrinter[T]
  ): RouteDtoHandler[T] =
    predefinedValue.fold(new RouteDtoHandler[T])(value => new RouteDtoHandlerWithPredefinedValue[T](value))
}

class RouteDtoValueWithJsonOpt[T] private (val value: T, val jsonString: Option[String]) {
  lazy val getJsonString: String = jsonString.getOrElse("")
}

object RouteDtoValueWithJsonOpt {

  def apply[T](value: T)(implicit ttag: TypeTag[T], jsonPrinter: JsonStringPrinter[T]): RouteDtoValueWithJsonOpt[T] =
    new RouteDtoValueWithJsonOpt[T](value, Option.when(ttag != implicitly[TypeTag[Unit]])(jsonPrinter.printJson(value)))

}

trait JsonStringPrinter[T] {
  def printJson(obj: T): String
}
