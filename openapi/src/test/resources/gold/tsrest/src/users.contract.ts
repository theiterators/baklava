import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { errorResponseSchema, userSchema } from "./schemas";
import { paginatedUsersSchema, photoUploadSchema, updateUserRequestSchema } from "./users.schemas";

export const users = initContract().router({
  get: {
    summary: 'List users',
    description: 'List users with pagination and optional role filter\n\nRequires authentication: bearerAuth (HTTP Bearer, JWT).',
    method: 'GET',
    path: '/users',
    query: z.object({page: z.number().int().nullish(), limit: z.number().int().nullish(), role: z.enum(["admin","guest","member"]).describe("User role within the system").nullish()}),
    responses: {
      200: paginatedUsersSchema
    }
  },
  byUserId: {
    delete: {
      summary: 'Delete user',
      description: 'Delete a user\n\nRequires authentication: bearerAuth (HTTP Bearer, JWT).',
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
      description: 'Fetch a single user by UUID\n\nRequires authentication: bearerAuth (HTTP Bearer, JWT).',
      method: 'GET',
      path: '/users/:userId',
      pathParams: z.object({userId: z.string().uuid()}),
      responses: {
        200: userSchema,
        404: errorResponseSchema
      }
    },
    put: {
      summary: 'Update user',
      description: 'Replace a user\'s profile (admin only)\n\nRequires authentication: bearerAuth (HTTP Bearer, JWT).',
      method: 'PUT',
      path: '/users/:userId',
      pathParams: z.object({userId: z.string().uuid()}),
      body: updateUserRequestSchema,
      responses: {
        200: userSchema
      }
    },
    photo: {
      post: {
        summary: 'Upload photo',
        description: 'Upload a profile photo alongside a caption as multipart/form-data\n\nRequires authentication: bearerAuth (HTTP Bearer, JWT).',
        method: 'POST',
        path: '/users/:userId/photo',
        pathParams: z.object({userId: z.string().uuid()}),
        contentType: 'multipart/form-data',
        body: z.object({caption: z.string(), photo: z.instanceof(File)}),
        responses: {
          201: photoUploadSchema
        }
      }
    }
  }
});
