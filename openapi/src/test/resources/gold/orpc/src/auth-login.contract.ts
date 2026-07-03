import { z } from "zod";
import { oc } from "@orpc/contract";

export const authLoginContract = {
  post: oc
    .route({
      method: 'POST',
      path: '/auth/login',
      summary: 'Login',
      description: 'Exchange HTTP Basic credentials for a JWT token',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      body: z.object({
        "client_id": z.string(),
        "grant_type": z.string()})
    }))
    .output(z.object({
        "token": z.string(),
        "user": z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})}))
};

export const authLoginErrors = {
  post: {
    401: z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
  }
};
