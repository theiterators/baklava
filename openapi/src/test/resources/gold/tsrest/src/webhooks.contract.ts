import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { webhookAckSchema, webhookPayloadSchema } from "./webhooks.schemas";

export const webhooks = initContract().router({
  post: {
    summary: 'Deliver webhook',
    description: 'Accept a webhook payload\n\nRequires authentication: apiKey (API key in header X-API-Key).',
    method: 'POST',
    path: '/webhooks',
    body: webhookPayloadSchema,
    responses: {
      202: z.union([webhookAckSchema, z.string()])
    }
  }
});
