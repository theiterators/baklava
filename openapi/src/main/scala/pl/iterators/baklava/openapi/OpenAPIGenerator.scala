package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import pl.iterators.baklava.{BaklavaRequestContext, BaklavaResponseContext}

import scala.jdk.CollectionConverters.*

object OpenAPIGenerator {
  def from(openAPI: OpenAPI, context: List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]): OpenAPI = {
    context.groupBy(_._1.symbolicPath).foreach { case (path, responses) =>
      val pathItem = new io.swagger.v3.oas.models.PathItem()
      responses.groupBy(_._1.method).foreach { case (method, responses) =>
        val operation          = new io.swagger.v3.oas.models.Operation()
        val operationResponses = new io.swagger.v3.oas.models.responses.ApiResponses()
        responses.foreach { case (ctx, response) =>
          val r = new io.swagger.v3.oas.models.responses.ApiResponse()
          ctx.responseDescription.foreach(r.setDescription)
          operationResponses
            .addApiResponse(response.status.status.toString, r)
        }
        operation.responses(operationResponses)
        responses.head._1.operationId.foreach(operation.setOperationId)
        responses.head._1.operationSummary.foreach(operation.setSummary)
        responses.head._1.operationDescription.foreach(operation.setDescription)
        operation.setTags(responses.head._1.operationTags.asJava)

        pathItem.operation(io.swagger.v3.oas.models.PathItem.HttpMethod.valueOf(method.get.value.toUpperCase), operation)
        responses.head._1.pathSummary.foreach(pathItem.setSummary)
        responses.head._1.pathDescription.foreach(pathItem.setDescription)
      }
      openAPI.path(path, pathItem)
    }
    openAPI
  }
}
