import { z } from "zod";

export const loginFormSchema = z.object({
        "client_id": z.string(),
        "grant_type": z.string()});
export type LoginForm = z.infer<typeof loginFormSchema>;
