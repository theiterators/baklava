import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { webhookPayloadSchema } from "./schemas";

export const webhooks = initContract().router({
  post: {
    summary: 'Deliver webhook',
    description: 'Accept a webhook payload',
    method: 'POST',
    path: '/webhooks',
    body: webhookPayloadSchema,
    responses: {
      202: z.union([z.object({
        "received": z.boolean()}), z.string()])
    }
  }
});
