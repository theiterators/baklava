package baklavaclient.webhooks

final case class WebhookAck(received: Boolean)

final case class WebhookPayload(data: String, event: String)
