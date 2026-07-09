import { z } from "zod";
import { oc } from "@orpc/contract";
import { createProjectRequestSchema, projectSchema } from "./schemas";

export const projectsContract = {
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
        data: z.object({
        "code": z.enum(["validation"]),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
      }
    })
};
