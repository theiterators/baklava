package pl.iterators.baklava.formatteropenapi.utils.builders

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.parameters.{Parameter, RequestBody}
import io.swagger.v3.oas.models.responses.ApiResponses

object OperationBuilder {
  def build(
      summary: String,
      description: String,
      parameters: List[Parameter],
      requestBody: Option[RequestBody],
      responses: ApiResponses
  ): Operation = {
    val operation = new Operation()
    operation.setSummary(summary)
    operation.setDescription(description)
    parameters.foreach(operation.addParametersItem)
    requestBody.foreach(operation.requestBody)
    operation.setResponses(responses)
    operation
  }
}
