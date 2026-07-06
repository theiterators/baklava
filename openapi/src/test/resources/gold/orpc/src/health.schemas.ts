import { z } from "zod";

export const healthResponseSchema = z.object({
        "status": z.string(),
        "uptimeSeconds": z.number().int()});
export type HealthResponse = z.infer<typeof healthResponseSchema>;
