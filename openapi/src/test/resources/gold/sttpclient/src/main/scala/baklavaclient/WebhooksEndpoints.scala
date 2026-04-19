package baklavaclient

import sttp.client4.*
import sttp.model.Uri

object WebhooksEndpoints {

  /** Deliver webhook — Accept a webhook payload */
  def deliverWebhook(
      bodyJson: String,
      apiKeyValue: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("webhooks"))
      .header("X-API-Key", apiKeyValue)
      .body(bodyJson)
      .contentType("application/json")
  }
}
