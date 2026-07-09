import { z } from "zod";

export const errorResponseSchema = z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()});
export type ErrorResponse = z.infer<typeof errorResponseSchema>;

export const healthResponseSchema = z.object({
        "status": z.string(),
        "uptimeSeconds": z.number().int()});
export type HealthResponse = z.infer<typeof healthResponseSchema>;

export const userSchema = z.object({
        "email": z.string(),
        "id": z.uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")});
export type User = z.infer<typeof userSchema>;
