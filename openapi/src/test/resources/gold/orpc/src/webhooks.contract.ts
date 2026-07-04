import { z } from "zod";
import { oc } from "@orpc/contract";

export const webhooksContract = {
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
      body: z.object({
        "data": z.string(),
        "event": z.string()})
    }))
    .output(z.union([z.object({
        "received": z.boolean()}), z.string()]))
};
