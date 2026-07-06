import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { errorResponseSchema, userSchema } from "./schemas";
import { loginFormSchema } from "./auth.schemas";

export const auth = initContract().router({
  login: {
    post: {
      summary: 'Login',
      description: 'Exchange HTTP Basic credentials for a JWT token',
      method: 'POST',
      path: '/auth/login',
      body: loginFormSchema,
      responses: {
        200: z.object({
          "token": z.string(),
          "user": userSchema}),
        401: errorResponseSchema
      }
    }
  }
});
