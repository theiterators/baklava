import { z } from "zod";
import { oc } from "@orpc/contract";
import { errorResponseSchema } from "./schemas";
import { loginFormSchema, loginResponseSchema } from "./auth.schemas";

export const auth = {
  login: {
    post: oc
      .route({
        method: 'POST',
        path: '/auth/login',
        summary: 'Login',
        description: 'Exchange HTTP Basic credentials for a JWT token',
        operationId: 'login',
        tags: ['Auth'],
        successStatus: 200,
        inputStructure: 'detailed',
        spec: (current) => ({ ...current, security: [{ basicAuth: [] }] })
      })
      .input(z.object({
        body: loginFormSchema
      }))
      .output(loginResponseSchema)
      .errors({
        'unauthorized': {
          status: 401,
          data: errorResponseSchema.extend({code: z.enum(["unauthorized"])})
        }
      })
  }
};
