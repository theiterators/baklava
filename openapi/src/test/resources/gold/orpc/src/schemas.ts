import { z } from "zod";

export const errorResponseSchema = z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()});

export const userSchema = z.object({
        "email": z.string(),
        "id": z.uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")});
