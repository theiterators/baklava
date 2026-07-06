import { z } from "zod";
import { oc } from "@orpc/contract";
import { errorResponseSchema } from "./schemas";
import { createProjectRequestSchema, createTaskRequestSchema, patchProjectRequestSchema, projectSchema, taskSchema } from "./projects.schemas";

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
        body: patchProjectRequestSchema
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
          body: createTaskRequestSchema
        }))
        .output(taskSchema)
    }
  }
};
