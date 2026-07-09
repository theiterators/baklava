import { z } from "zod";
import { oc } from "@orpc/contract";
import { webhookPayloadSchema } from "./schemas";

export const webhooks = {
  post: oc
    .route({
      method: 'POST',
      path: '/webhooks',
      summary: 'Deliver webhook',
      description: 'Accept a webhook payload',
      operationId: 'deliverWebhook',
      tags: ['Webhooks'],
      successStatus: 202,
      inputStructure: 'detailed'
    })
    .input(z.object({
      body: webhookPayloadSchema
    }))
    .output(z.union([z.object({
        "received": z.boolean()}), z.string()]))
};
