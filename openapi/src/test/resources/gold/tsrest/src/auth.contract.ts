import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { errorResponseSchema } from "./schemas";
import { loginFormSchema, loginResponseSchema } from "./auth.schemas";

export const auth = initContract().router({
  login: {
    post: {
      summary: 'Login',
      description: 'Exchange HTTP Basic credentials for a JWT token\n\nRequires authentication: basicAuth (HTTP Basic).',
      method: 'POST',
      path: '/auth/login',
      body: loginFormSchema,
      responses: {
        200: loginResponseSchema,
        401: errorResponseSchema
      }
    }
  }
});
