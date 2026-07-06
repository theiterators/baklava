import { z } from "zod";

export const webhookPayloadSchema = z.object({
        "data": z.string(),
        "event": z.string()});
export type WebhookPayload = z.infer<typeof webhookPayloadSchema>;
