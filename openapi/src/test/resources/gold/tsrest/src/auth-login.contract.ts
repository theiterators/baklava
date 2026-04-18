import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const authLoginContract = initContract().router({
  post: {
    summary: 'Login',
    description: 'Exchange HTTP Basic credentials for a JWT token',
    method: 'POST',
    path: '/auth/login',
    body: z.object({
        "client_id": z.string(),
        "grant_type": z.string()}),
    responses: {
      200: z.object({
        "token": z.string(),
        "user": z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})}),
      401: z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
    }
  }
});
