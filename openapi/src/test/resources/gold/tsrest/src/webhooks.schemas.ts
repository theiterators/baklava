import { z } from "zod";

export const webhookPayloadSchema = z.object({
        "data": z.string(),
        "event": z.string()});
