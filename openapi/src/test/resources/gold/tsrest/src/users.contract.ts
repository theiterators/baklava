import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const usersContract = initContract().router({
  get: {
    summary: 'List users',
    description: 'List users with pagination and optional role filter',
    method: 'GET',
    path: '/users',
    query: z.object({page: z.number().int().nullish(), limit: z.number().int().nullish(), role: z.enum(["admin","guest","member"]).describe("User role within the system").nullish()}),
    responses: {
      200: z.object({
        "limit": z.number().int(),
        "page": z.number().int(),
        "total": z.number().int(),
        "users": z.array(z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")}))})
    }
  }
});
