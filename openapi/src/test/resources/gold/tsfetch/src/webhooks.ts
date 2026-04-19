import { BaklavaClient, BaklavaHttpError } from "./client";
import type * as T from "./types";

/** Deliver webhook — Accept a webhook payload */
export async function deliverWebhook(client: BaklavaClient, params: {
  body: T.WebhookPayload;
}): Promise<T.WebhookAck> {
  const url = new URL(`${client.baseUrl}/webhooks`);
  let __ret!: T.WebhookAck;
  const res = await client.fetch(url.toString(), {
    method: "POST",
    headers: {
    ...client.authHeaders(),
    "Content-Type": "application/json",
    ...(client.apiKeys?.["X-API-Key"] ? { "X-API-Key": client.apiKeys["X-API-Key"] } : {}),
    },
    body: JSON.stringify(params.body),
  });
  const text = await res.text();
  if (!res.ok) throw new BaklavaHttpError(res.status, text);
  return (text ? JSON.parse(text) : undefined) as typeof __ret;
}
