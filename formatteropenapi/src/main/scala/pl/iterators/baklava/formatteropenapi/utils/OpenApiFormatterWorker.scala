package pl.iterators.baklava.formatteropenapi.utils

import io.swagger.v3.oas.models._
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media._
import io.swagger.v3.oas.models.parameters.{Parameter, RequestBody}
import io.swagger.v3.oas.models.responses.{ApiResponse, ApiResponses}
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import pl.iterators.baklava.core.model._
import pl.iterators.baklava.formatteropenapi.utils.builders.{OpenApiBuilder, OperationBuilder, PathItemBuilder}
import pl.iterators.kebs.jsonschema.JsonSchemaWrapper

class OpenApiFormatterWorker(jsonSchemaToSwaggerSchemaWorker: JsonSchemaToSwaggerSchemaWorker) {

  def generateOpenApi(routesList: List[EnrichedRouteRepresentation[_, _]]): OpenAPI = {
    OpenApiBuilder.build(
      info = new Info().title("Autogenerated API doc").version("1"),
      components = routeListToComponents(routesList),
      paths = routeListToPaths(routesList)
    )
  }

  private def routeListToComponents(routesList: List[EnrichedRouteRepresentation[_, _]]): Components = {
    val components = new Components()
    routesList.flatMap(routeToSchemaWithName).foreach {
      case (name, schema) =>
        components.addSchemas(name, schema)
    }
    routesList
      .flatMap(_.routeRepresentation.authentication)
      .distinct
      .map(stringToSecuritySchema)
      .foreach { case (name, schema) => components.addSecuritySchemes(name, schema) }
    components
  }

  private def routeToSchemaWithName(route: EnrichedRouteRepresentation[_, _]): List[(String, Schema[_])] = {
    List(
      route.routeRepresentation.request,
      route.routeRepresentation.response
    ).flatMap { dto =>
      dto.scalaClassOpt.map { scalaClassName =>
        (
          schemaClassName(scalaClassName),
          swaggerSchema(dto.jsonSchemaWrapper)
        )
      }
    }
  }

  private def routeListToPaths(routesList: List[EnrichedRouteRepresentation[_, _]]): Paths = {
    val paths = new Paths()
    routesList.groupBy(_.routeRepresentation.path).toList.sortBy(_._1).foreach {
      case (path, routes) =>
        paths.addPathItem(path, routeGroupedByPathToPathItem(path, routes))
    }
    paths
  }

  private def routeGroupedByPathToPathItem(path: String, routes: List[EnrichedRouteRepresentation[_, _]]): PathItem = {
    PathItemBuilder.build(
      parameters = extractParamsFromPath(path),
      get = extractOperationFromGroupedByPath(routes, "GET"),
      post = extractOperationFromGroupedByPath(routes, "POST"),
      patch = extractOperationFromGroupedByPath(routes, "PATCH"),
      put = extractOperationFromGroupedByPath(routes, "PUT"),
      delete = extractOperationFromGroupedByPath(routes, "DELETE"),
    )
  }

  private def extractOperationFromGroupedByPath(grouped: List[EnrichedRouteRepresentation[_, _]], operation: String): Option[Operation] = {
    grouped
      .find(_.routeRepresentation.method.toUpperCase == operation.toUpperCase)
      .map(routeToOperation)
  }

  private def routeToOperation(route: EnrichedRouteRepresentation[_, _]): Operation = {
    OperationBuilder.build(
      summary = route.routeRepresentation.description,
      description = route.enrichDescriptions.map(_.description).mkString("\n"),
      parameters = queryParamsToParams(route.routeRepresentation.parameters) ++ headersToParams(route.routeRepresentation.headers),
      requestBody = routeToRequestBody(route),
      responses = routeToApiResponses(route),
      security = routeToSecurity(route)
    )
  }

  private def extractParamsFromPath(path: String): List[Parameter] = {
    val pattern = """\{(.*?)\}""".r
    pattern.findAllMatchIn(path).toList.map { m =>
      val p = new Parameter()
      p.setName(m.group(1))
      p.setIn("path")
      p.setSchema(new StringSchema)
      p
    }
  }

  private def queryParamsToParams(parameters: List[RouteParameterRepresentation[_]]): List[Parameter] = {
    parameters.map { param =>
      val p = new Parameter()
      p.setName(param.name)
      p.setIn("query")
      p.setExample(param.sampleValue)
      p.setRequired(param.required)
      p.setSchema(new StringSchema)
      p
    }
  }

  private def headersToParams(parameters: List[RouteHeaderRepresentation]): List[Parameter] = {
    parameters.map { header =>
      val p = new Parameter()
      p.setName(header.name)
      p.setIn("header")
      p.setRequired(header.required)
      p.setSchema(new StringSchema)
      p
    }
  }

  private def routeToRequestBody(route: EnrichedRouteRepresentation[_, _]): Option[RequestBody] = {
    route.routeRepresentation.request.minimal.jsonOpt.map { _ =>
      val mt = routeDtoHandlerToMediaType(route.routeRepresentation.request)

      val apiRequest = new RequestBody()
      apiRequest.setRequired(true)
      apiRequest.setContent(new Content().addMediaType("application/json", mt))
      apiRequest
    }
  }

  private def routeToApiResponses(route: EnrichedRouteRepresentation[_, _]): ApiResponses = {
    val apiResponses = new ApiResponses()
    route.enrichDescriptions
      .groupBy(_.statusCodeOpt)
      .filter(_._1.isDefined)
      .toList
      .sortBy(_._1.map(_.intValue()))
      .foreach {
        case (codeOpt, desc) =>
          val code        = codeOpt.get //get is safe here
          val apiResponse = new ApiResponse()

          apiResponse.setDescription(desc.map(_.description).mkString("\n"))
          if (code.intValue >= 200 && code.intValue < 204) {

            val mt = routeDtoHandlerToMediaType(route.routeRepresentation.response)
            apiResponse.setContent(new Content().addMediaType("application/json", mt))
          }

          apiResponses.addApiResponse(code.intValue.toString, apiResponse)
      }
    apiResponses
  }

  private def routeToSecurity(route: EnrichedRouteRepresentation[_, _]): List[SecurityRequirement] =
    route.routeRepresentation.authentication.map {
      case RouteSecurity.Bearer =>
        val security = new SecurityRequirement()
        security.addList("bearerAuth")
        security
      case RouteSecurity.Basic =>
        val security = new SecurityRequirement()
        security.addList("basicAuth")
        security
    }

  private def stringToSecuritySchema(name: RouteSecurity): (String, SecurityScheme) = {
    name match {
      case RouteSecurity.Bearer =>
        val securityScheme = new SecurityScheme()
        securityScheme.setScheme("bearer")
        securityScheme.setBearerFormat("JWT")
        securityScheme.setType(SecurityScheme.Type.HTTP)
        ("bearerAuth", securityScheme)
      case RouteSecurity.Basic =>
        val securityScheme = new SecurityScheme()
        securityScheme.setScheme("basic")
        securityScheme.setType(SecurityScheme.Type.HTTP)
        ("basicAuth", securityScheme)
    }
  }

  private def routeDtoHandlerToMediaType(dto: RouteDtoHandler[_]): MediaType = {
    val mt = new MediaType

    dto.minimal.jsonOpt.foreach { json =>
      val example = new Example()
      example.setValue(json.prettyPrint)
      mt.addExamples("minimal", example)

      val schema = new Schema
      schema.setType(swaggerSchema(dto.jsonSchemaWrapper).getType)
      schema.set$ref(schemaRefName(dto.scalaClassOpt.get))
      mt.setSchema(schema)
    }

    dto.maximal.jsonOpt.foreach { json =>
      val example = new Example()
      example.setValue(json.prettyPrint)
      mt.addExamples("maximal", example)
    }

    mt
  }

  private def swaggerSchema[T](jsonSchema: JsonSchemaWrapper[T]): Schema[_] = {
    jsonSchemaToSwaggerSchemaWorker.convert(jsonSchema.schema)
  }
  private def schemaRefName(name: String): String =
    s"#/components/schemas/${schemaClassName(name)}"

  private def schemaClassName(name: String): String =
    name.replaceAll("\\[", "_").replaceAll("\\]", "")
}
