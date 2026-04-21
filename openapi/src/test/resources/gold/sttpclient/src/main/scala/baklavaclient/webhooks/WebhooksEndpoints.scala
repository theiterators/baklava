package baklavaclient.webhooks

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.model.Uri

object WebhooksEndpoints {

  /** Deliver webhook — Accept a webhook payload */
  def deliverWebhook(
      body: WebhookPayload,
      apiKeyValue: String,
      baseUri: Uri
  ): Request[Either[ResponseException[String], WebhookAck]] = {
    basicRequest
      .post(baseUri.addPath("webhooks"))
      .header("X-API-Key", apiKeyValue)
      .body(body.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[WebhookAck])
  }
}
