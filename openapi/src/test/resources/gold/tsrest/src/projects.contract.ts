import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { errorResponseSchema } from "./schemas";
import { createProjectRequestSchema, projectSchema, taskSchema } from "./projects.schemas";

export const projects = initContract().router({
  get: {
    summary: 'List projects',
    description: 'List projects, optionally filtered by status',
    method: 'GET',
    path: '/projects',
    query: z.object({status: z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()}),
    responses: {
      200: z.array(projectSchema)
    }
  },
  post: {
    summary: 'Create project',
    description: 'Create a new project',
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
      description: 'Partially update a project',
      method: 'PATCH',
      path: '/projects/:projectId',
      pathParams: z.object({projectId: z.number().int()}),
      body: z.object({
          "description": z.string().nullish(),
          "name": z.string().nullish(),
          "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()}),
      responses: {
        200: projectSchema
      }
    },
    tasks: {
      get: {
        summary: 'List tasks',
        description: 'List all tasks in a project',
        method: 'GET',
        path: '/projects/:projectId/tasks',
        pathParams: z.object({projectId: z.number().int()}),
        responses: {
          200: z.array(taskSchema)
        }
      },
      post: {
        summary: 'Create task',
        description: 'Create a task in a project',
        method: 'POST',
        path: '/projects/:projectId/tasks',
        pathParams: z.object({projectId: z.number().int()}),
        body: z.object({
            "description": z.string().nullish(),
            "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
            "title": z.string()}),
        responses: {
          201: taskSchema
        }
      }
    }
  }
});
