import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const usersUserIdContract = initContract().router({
  delete: {
    summary: 'Delete user',
    description: 'Delete a user',
    method: 'DELETE',
    path: '/users/:userId',
    pathParams: z.object({userId: z.string().uuid()}),
    body: z.undefined(),
    responses: {
      204: z.undefined()
    }
  },
  get: {
    summary: 'Get user',
    description: 'Fetch a single user by UUID',
    method: 'GET',
    path: '/users/:userId',
    pathParams: z.object({userId: z.string().uuid()}),
    responses: {
      200: z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")}),
      404: z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
    }
  },
  put: {
    summary: 'Update user',
    description: 'Replace a user\'s profile (admin only)',
    method: 'PUT',
    path: '/users/:userId',
    pathParams: z.object({userId: z.string().uuid()}),
    body: z.object({
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")}),
    responses: {
      200: z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")})
    }
  }
});
