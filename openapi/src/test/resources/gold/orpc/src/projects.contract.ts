import { z } from "zod";
import { oc } from "@orpc/contract";
import { createProjectRequestSchema, errorResponseSchema, projectSchema, taskSchema } from "./schemas";

export const projects = {
  get: oc
    .route({
      method: 'GET',
      path: '/projects',
      summary: 'List projects',
      description: 'List projects, optionally filtered by status',
      operationId: 'listProjects',
      tags: ['Projects'],
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      query: z.object({status: z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()}).optional()
    }))
    .output(z.array(projectSchema)),
  post: oc
    .route({
      method: 'POST',
      path: '/projects',
      summary: 'Create project',
      description: 'Create a new project',
      operationId: 'createProject',
      tags: ['Projects'],
      successStatus: 201,
      inputStructure: 'detailed'
    })
    .input(z.object({
      body: createProjectRequestSchema
    }))
    .output(projectSchema)
    .errors({
      'validation': {
        status: 400,
        data: errorResponseSchema.extend({code: z.enum(["validation"])})
      }
    }),
  byProjectId: {
    patch: oc
      .route({
        method: 'PATCH',
        path: '/projects/{projectId}',
        summary: 'Patch project',
        description: 'Partially update a project',
        operationId: 'patchProject',
        tags: ['Projects'],
        successStatus: 200,
        inputStructure: 'detailed'
      })
      .input(z.object({
        params: z.object({projectId: z.number().int()}),
        body: z.object({
          "description": z.string().nullish(),
          "name": z.string().nullish(),
          "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()})
      }))
      .output(projectSchema),
    tasks: {
      get: oc
        .route({
          method: 'GET',
          path: '/projects/{projectId}/tasks',
          summary: 'List tasks',
          description: 'List all tasks in a project',
          operationId: 'listTasks',
          tags: ['Tasks'],
          successStatus: 200,
          inputStructure: 'detailed'
        })
        .input(z.object({
          params: z.object({projectId: z.number().int()})
        }))
        .output(z.array(taskSchema)),
      post: oc
        .route({
          method: 'POST',
          path: '/projects/{projectId}/tasks',
          summary: 'Create task',
          description: 'Create a task in a project',
          operationId: 'createTask',
          tags: ['Tasks'],
          successStatus: 201,
          inputStructure: 'detailed'
        })
        .input(z.object({
          params: z.object({projectId: z.number().int()}),
          body: z.object({
            "description": z.string().nullish(),
            "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
            "title": z.string()})
        }))
        .output(taskSchema)
    }
  }
};
