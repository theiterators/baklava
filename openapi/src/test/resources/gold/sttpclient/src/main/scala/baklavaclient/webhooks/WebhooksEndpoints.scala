package baklavaclient.webhooks

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.model.Uri

object WebhooksEndpoints {

  /** Deliver webhook — Accept a webhook payload */
  def deliverWebhook(
      baseUri: Uri,
      apiKeyValue: String,
      body: WebhookPayload
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("webhooks"))
      .header("X-API-Key", apiKeyValue)
      .body(body.asJson.noSpaces)
      .contentType("application/json")
  }
}
