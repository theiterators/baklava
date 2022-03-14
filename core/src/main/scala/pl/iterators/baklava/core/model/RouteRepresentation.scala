package pl.iterators.baklava.core.model

import pl.iterators.kebs.jsonschema.JsonSchemaWrapper
import pl.iterators.kebs.scalacheck.AllGenerators
import spray.json.{JsonWriter, _}
import pl.iterators.baklava.core.utils.option.RichOptionCompanion

import scala.reflect.runtime.universe._

case class RouteRepresentation[Request, Response](
    description: String,
    method: String,
    path: String,
    parameters: List[RouteParameterRepresentation[_]] = Nil,
    headers: List[RouteHeaderRepresentation] = Nil,
    requestPredefinedValue: Option[Request] = None,
    responsePredefinedValue: Option[Response] = None,
    authentication: List[RouteSecurity] = List.empty
)(implicit
  requestTypeTag: TypeTag[Request],
  requestGenerators: AllGenerators[Request],
  val requestJsonSchemaWrapper: JsonSchemaWrapper[Request],
  requestJsonWriter: JsonWriter[Request],
  responseTypeTag: TypeTag[Response],
  responseGenerators: AllGenerators[Response],
  val responseJsonSchemaWrapper: JsonSchemaWrapper[Response],
  responseJsonWriter: JsonWriter[Response]) {

  lazy val name: String = s"$method $path"

  lazy val routePathWithRequiredParameters: String = {
    val requiredParams = parameters.filter(_.required)
    val parametersPath = Option
      .when(requiredParams.nonEmpty) {
        requiredParams
          .map { param =>
            s"${param.name}=${param.marshall}"
          }
          .mkString("?", "&", "")
      }
      .getOrElse("")

    s"$path$parametersPath"
  }

  lazy val routePathWithAllParameters: String = {
    val parametersPath = Option
      .when(parameters.nonEmpty) {
        parameters
          .map { param =>
            s"${param.name}=${param.marshall}"
          }
          .mkString("?", "&", "")
      }
      .getOrElse("")

    s"$path$parametersPath"
  }

  lazy val request: RouteDtoHandler[Request]   = RouteDtoHandler(requestPredefinedValue)
  lazy val response: RouteDtoHandler[Response] = RouteDtoHandler(responsePredefinedValue)

  def getParamValue[T](name: String): Option[T] =
    parameters.find(_.name == name).map(_.sampleValue.asInstanceOf[T])

}
