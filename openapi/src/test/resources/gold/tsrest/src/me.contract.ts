import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { userSchema } from "./schemas";

export const me = initContract().router({
  get: {
    summary: 'Who am I',
    description: 'Return the profile of the currently authenticated user\n\nRequires authentication: bearerAuth (HTTP Bearer, JWT).',
    method: 'GET',
    path: '/me',
    responses: {
      200: userSchema
    }
  }
});
