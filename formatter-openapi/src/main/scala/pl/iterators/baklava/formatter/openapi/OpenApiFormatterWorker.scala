package pl.iterators.baklava.formatter.openapi

import io.swagger.v3.oas.models._
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media._
import io.swagger.v3.oas.models.parameters.{Parameter, RequestBody}
import io.swagger.v3.oas.models.responses.{ApiResponse, ApiResponses}
import io.swagger.v3.oas.models.security.{SecurityRequirement, SecurityScheme}
import pl.iterators.baklava.core.model._
import pl.iterators.baklava.formatter.openapi.builders.{OpenApiBuilder, OperationBuilder, PathItemBuilder}
import pl.iterators.kebs.jsonschema.JsonSchemaWrapper

import scala.jdk.CollectionConverters._

class OpenApiFormatterWorker(jsonSchemaToSwaggerSchemaWorker: JsonSchemaToSwaggerSchemaWorker) {

  def generateOpenApi(routesList: List[EnrichedRouteRepresentation[_, _]]): OpenAPI =
    OpenApiBuilder.build(
      info = new Info().title("Autogenerated API doc").version("1"),
      components = routeListToComponents(routesList),
      paths = routeListToPaths(routesList)
    )

  private def routeListToComponents(routesList: List[EnrichedRouteRepresentation[_, _]]): Components = {
    val components = new Components()
    routesList.flatMap(routeToSchemaWithName).foreach { case (name, schema) =>
      components.addSchemas(name, schema)
    }
    routesList.flatMap(routeSecurityGroupToSecuritySchemaWithName).foreach { case (name, schema) =>
      components.addSecuritySchemes(name, schema)
    }
    components
  }

  private def routeToSchemaWithName(route: EnrichedRouteRepresentation[_, _]): List[(String, Schema[_])] =
    List(route.routeRepresentation.request, route.routeRepresentation.response).flatMap { dto =>
      dto.scalaClassOpt.map { scalaClassName =>
        (schemaClassName(scalaClassName), swaggerSchema(dto.jsonSchemaWrapper))
      }
    }

  private def routeListToPaths(routesList: List[EnrichedRouteRepresentation[_, _]]): Paths = {
    val paths = new Paths()
    routesList.groupBy(_.routeRepresentation.path).toList.sortBy(_._1).foreach { case (path, routes) =>
      paths.addPathItem(path, routeGroupedByPathToPathItem(path, routes))
    }
    paths
  }

  private def routeGroupedByPathToPathItem(path: String, routes: List[EnrichedRouteRepresentation[_, _]]): PathItem =
    PathItemBuilder.build(
      parameters = extractParamsFromPath(path),
      get = extractOperationFromGroupedByPath(routes, "GET"),
      post = extractOperationFromGroupedByPath(routes, "POST"),
      patch = extractOperationFromGroupedByPath(routes, "PATCH"),
      put = extractOperationFromGroupedByPath(routes, "PUT"),
      delete = extractOperationFromGroupedByPath(routes, "DELETE")
    )

  private def extractOperationFromGroupedByPath(grouped: List[EnrichedRouteRepresentation[_, _]], operation: String): Option[Operation] =
    grouped
      .find(_.routeRepresentation.method.toUpperCase == operation.toUpperCase)
      .map(routeToOperation)

  private def routeToOperation(route: EnrichedRouteRepresentation[_, _]): Operation =
    OperationBuilder.build(
      summary = route.routeRepresentation.description,
      description = route.routeRepresentation.extendedDescription
        .getOrElse(route.enrichDescriptions.map(_.description).mkString("\n")),
      parameters = queryParamsToParams(route.routeRepresentation.parameters) ++ headersToParams(route.routeRepresentation.headers),
      requestBody = routeToRequestBody(route),
      responses = routeToApiResponses(route),
      security = routeToSecurity(route)
    )

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

  private def queryParamsToParams(parameters: List[RouteParameterRepresentation[_]]): List[Parameter] =
    parameters.map { param =>
      val itemSchema = new StringSchema
      itemSchema.setExample(param.valueGenerator())
      param.enums.foreach { values =>
        itemSchema.setEnum(values.toList.asJava)
      }

      val schema: Schema[_] =
        if (param.seq) {
          val arraySchema = new ArraySchema
          arraySchema.setItems(itemSchema)
          arraySchema
        } else itemSchema

      val p = new Parameter()
      if (param.seq) p.setName(s"${param.name}[]") else p.setName(param.name)
      p.setIn("query")
      p.setRequired(param.required)
      p.setSchema(schema)
      p
    }

  private def headersToParams(parameters: List[RouteHeaderRepresentation]): List[Parameter] =
    parameters.map { header =>
      val p = new Parameter()
      p.setName(header.name)
      p.setIn("header")
      p.setRequired(header.required)
      p.setSchema(new StringSchema)
      p
    }

  private def routeToRequestBody(route: EnrichedRouteRepresentation[_, _]): Option[RequestBody] =
    route.routeRepresentation.request.minimal.jsonString.map { _ =>
      val mt = routeDtoHandlerToMediaType(route.routeRepresentation.request)

      val apiRequest = new RequestBody()
      apiRequest.setRequired(true)
      apiRequest.setContent(new Content().addMediaType("application/json", mt))
      apiRequest
    }

  private def routeToApiResponses(route: EnrichedRouteRepresentation[_, _]): ApiResponses = {
    val apiResponses = new ApiResponses()
    route.enrichDescriptions
      .groupBy(_.statusCodeOpt)
      .filter(_._1.isDefined)
      .toList
      .sortBy(_._1.map(_.intValue()))
      .foreach { case (codeOpt, desc) =>
        val code        = codeOpt.get // get is safe here
        val apiResponse = new ApiResponse()
        apiResponse.setDescription(desc.map(_.description).mkString("\n"))

        if (code.intValue >= 200 && code.intValue < 204) {

          val mt = routeDtoHandlerToMediaType(route.routeRepresentation.response)
          apiResponse.setContent(new Content().addMediaType("application/json", mt))
        } else {
          val mt = new MediaType
          route.routeRepresentation.errorResponses.filter(er => er.status == code.intValue()).foreach { errorResponse =>
            val example = new Example()
            example.setValue(s"""{"type": "${errorResponse.jsonData.`type`}", "status": ${errorResponse.status}}""")
            mt.addExamples(errorResponse.resultName, example)
          }
          if (Option(mt.getExamples).nonEmpty) {
            apiResponse.setContent(new Content().addMediaType("application/json", mt))
          }
        }
        apiResponses.addApiResponse(code.intValue.toString, apiResponse)
      }
    apiResponses
  }

  private def routeToSecurity(route: EnrichedRouteRepresentation[_, _]): List[SecurityRequirement] =
    route.routeRepresentation.authentication.map { routeSecurityGroup =>
      val security = new SecurityRequirement()
      routeSecurityGroup.list.map(_.schemaName).foreach(security.addList)
      security
    }

  private def routeSecurityGroupToSecuritySchemaWithName(route: EnrichedRouteRepresentation[_, _]): List[(String, SecurityScheme)] =
    route.routeRepresentation.authentication.flatMap(_.list).distinct.map {
      case RouteSecurity.Bearer(schemaName) =>
        val securityScheme = new SecurityScheme()
        securityScheme.setScheme("bearer")
        securityScheme.setBearerFormat("JWT")
        securityScheme.setType(SecurityScheme.Type.HTTP)
        (schemaName, securityScheme)
      case RouteSecurity.Basic(schemaName) =>
        val securityScheme = new SecurityScheme()
        securityScheme.setScheme("basic")
        securityScheme.setType(SecurityScheme.Type.HTTP)
        (schemaName, securityScheme)
      case RouteSecurity.HeaderApiKey(name, schemaName) =>
        val securityScheme = new SecurityScheme()
        securityScheme.setType(SecurityScheme.Type.APIKEY)
        securityScheme.setIn(SecurityScheme.In.HEADER)
        securityScheme.setName(name)
        (schemaName, securityScheme)
      case RouteSecurity.QueryApiKey(name, schemaName) =>
        val securityScheme = new SecurityScheme()
        securityScheme.setType(SecurityScheme.Type.APIKEY)
        securityScheme.setIn(SecurityScheme.In.QUERY)
        securityScheme.setName(name)
        (schemaName, securityScheme)
      case RouteSecurity.CookieApiKey(name, schemaName) =>
        val securityScheme = new SecurityScheme()
        securityScheme.setType(SecurityScheme.Type.APIKEY)
        securityScheme.setIn(SecurityScheme.In.COOKIE)
        securityScheme.setName(name)
        (schemaName, securityScheme)
    }

  private def routeDtoHandlerToMediaType(dto: RouteDtoHandler[_]): MediaType = {
    val mt = new MediaType

    dto.minimal.jsonString.foreach { json =>
      val example = new Example()
      example.setValue(json)
      mt.addExamples("minimal", example)

      val schema = new Schema
      schema.setType(swaggerSchema(dto.jsonSchemaWrapper).getType)
      schema.set$ref(schemaRefName(dto.scalaClassOpt.get))
      mt.setSchema(schema)
    }

    dto.maximal.jsonString.foreach { json =>
      val example = new Example()
      example.setValue(json)
      mt.addExamples("maximal", example)
    }

    mt
  }

  private def swaggerSchema[T](jsonSchema: JsonSchemaWrapper[T]): Schema[_] =
    jsonSchemaToSwaggerSchemaWorker.convertMatch(jsonSchema.schema)
  private def schemaRefName(name: String): String =
    s"#/components/schemas/${schemaClassName(name)}"

  private def schemaClassName(name: String): String =
    name.replaceAll("\\[", "_").replaceAll("\\]", "")
}
