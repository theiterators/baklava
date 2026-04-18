import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const meContract = initContract().router({
  get: {
    summary: 'Who am I',
    description: 'Return the profile of the currently authenticated user',
    method: 'GET',
    path: '/me',
    responses: {
      200: z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})
    }
  }
});
