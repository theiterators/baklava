import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { errorResponseSchema } from "./schemas";
import { createProjectRequestSchema, createTaskRequestSchema, patchProjectRequestSchema, projectSchema, taskSchema } from "./projects.schemas";

export const projects = initContract().router({
  get: {
    summary: 'List projects',
    description: 'List projects, optionally filtered by status\n\nRequires authentication: oauth2 (OAuth2).',
    method: 'GET',
    path: '/projects',
    query: z.object({status: z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()}),
    responses: {
      200: z.array(projectSchema)
    }
  },
  post: {
    summary: 'Create project',
    description: 'Create a new project\n\nRequires authentication: oauth2 (OAuth2).',
    method: 'POST',
    path: '/projects',
    body: createProjectRequestSchema,
    responses: {
      201: projectSchema,
      400: errorResponseSchema
    }
  },
  byProjectId: {
    patch: {
      summary: 'Patch project',
      description: 'Partially update a project\n\nRequires authentication: oauth2 (OAuth2).',
      method: 'PATCH',
      path: '/projects/:projectId',
      pathParams: z.object({projectId: z.number().int()}),
      body: patchProjectRequestSchema,
      responses: {
        200: projectSchema
      }
    },
    tasks: {
      get: {
        summary: 'List tasks',
        description: 'List all tasks in a project\n\nRequires authentication: oauth2 (OAuth2).',
        method: 'GET',
        path: '/projects/:projectId/tasks',
        pathParams: z.object({projectId: z.number().int()}),
        responses: {
          200: z.array(taskSchema)
        }
      },
      post: {
        summary: 'Create task',
        description: 'Create a task in a project\n\nRequires authentication: oauth2 (OAuth2).',
        method: 'POST',
        path: '/projects/:projectId/tasks',
        pathParams: z.object({projectId: z.number().int()}),
        body: createTaskRequestSchema,
        responses: {
          201: taskSchema
        }
      }
    }
  }
});
