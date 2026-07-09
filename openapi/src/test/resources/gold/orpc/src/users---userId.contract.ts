import { z } from "zod";
import { oc } from "@orpc/contract";
import { userSchema } from "./schemas";

export const usersUserIdContract = {
  delete: oc
    .route({
      method: 'DELETE',
      path: '/users/{userId}',
      summary: 'Delete user',
      description: 'Delete a user',
      operationId: 'deleteUser',
      tags: ['Users'],
      successStatus: 204,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.uuid()})
    }))
    .output(z.void()),
  get: oc
    .route({
      method: 'GET',
      path: '/users/{userId}',
      summary: 'Get user',
      description: 'Fetch a single user by UUID',
      operationId: 'getUser',
      tags: ['Users'],
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.uuid()})
    }))
    .output(userSchema)
    .errors({
      'not_found': {
        status: 404,
        data: z.object({
        "code": z.enum(["not_found"]),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
      }
    }),
  put: oc
    .route({
      method: 'PUT',
      path: '/users/{userId}',
      summary: 'Update user',
      description: 'Replace a user\'s profile (admin only)',
      operationId: 'updateUser',
      tags: ['Users'],
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.uuid()}),
      body: z.object({
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})
    }))
    .output(userSchema)
};
