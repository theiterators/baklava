import { z } from "zod";
import { oc } from "@orpc/contract";

export const usersContract = {
  get: oc
    .route({
      method: 'GET',
      path: '/users',
      summary: 'List users',
      description: 'List users with pagination and optional role filter',
      operationId: 'listUsers',
      tags: ['Users'],
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      query: z.object({page: z.number().int().nullish(), limit: z.number().int().nullish(), role: z.enum(["admin","guest","member"]).describe("User role within the system").nullish()}).optional()
    }))
    .output(z.object({
        "limit": z.number().int(),
        "page": z.number().int(),
        "total": z.number().int(),
        "users": z.array(z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")}))}))
};
