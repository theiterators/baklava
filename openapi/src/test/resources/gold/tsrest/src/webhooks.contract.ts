import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const webhooksContract = initContract().router({
  post: {
    summary: 'Deliver webhook',
    description: 'Accept a webhook payload',
    method: 'POST',
    path: '/webhooks',
    body: z.object({
        "data": z.string(),
        "event": z.string()}),
    responses: {
      202: z.union([z.object({
        "received": z.boolean()}), z.string()])
    }
  }
});
