import { z } from "zod";
import { oc } from "@orpc/contract";

export const usersUserIdContract = {
  delete: oc
    .route({
      method: 'DELETE',
      path: '/users/{userId}',
      summary: 'Delete user',
      description: 'Delete a user',
      successStatus: 204,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.string().uuid()})
    }))
    .output(z.void()),
  get: oc
    .route({
      method: 'GET',
      path: '/users/{userId}',
      summary: 'Get user',
      description: 'Fetch a single user by UUID',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.string().uuid()})
    }))
    .output(z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})),
  put: oc
    .route({
      method: 'PUT',
      path: '/users/{userId}',
      summary: 'Update user',
      description: 'Replace a user\'s profile (admin only)',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.string().uuid()}),
      body: z.object({
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})
    }))
    .output(z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")}))
};

export const usersUserIdErrors = {
  get: {
    404: z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
  }
};
