package pl.iterators.baklava.formatter.openapi.builders

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.{Parameter, RequestBody}
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement

import scala.jdk.CollectionConverters._

object OperationBuilder {
  def build(
    summary: String,
    description: String,
    parameters: List[Parameter],
    requestBody: Option[RequestBody],
    responses: ApiResponses,
    security: List[SecurityRequirement]
  ): Operation = {
    val operation = new Operation()
    operation.setSummary(summary)
    operation.setDescription(description)
    parameters.foreach(operation.addParametersItem)
    requestBody.foreach(operation.requestBody)
    operation.setResponses(responses)
    if (security.nonEmpty) {
      operation.setSecurity(security.asJava)
    }
    operation
  }
}
