import { z } from "zod";
import { userSchema } from "./schemas";

export const loginFormSchema = z.object({
        "client_id": z.string(),
        "grant_type": z.string()});
export type LoginForm = z.infer<typeof loginFormSchema>;

export const loginResponseSchema = z.object({
        "token": z.string(),
        "user": userSchema});
export type LoginResponse = z.infer<typeof loginResponseSchema>;
