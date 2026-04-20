export interface WebhookAck {
  received: boolean;
}

export interface WebhookPayload {
  data: string;
  event: string;
}
