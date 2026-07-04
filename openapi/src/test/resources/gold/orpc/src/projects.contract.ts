import { z } from "zod";
import { oc } from "@orpc/contract";

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
    .output(z.array(z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.string().uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")}))),
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
      body: z.object({
        "description": z.string().nullish(),
        "name": z.string(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")})
    }))
    .output(z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.string().uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")}))
    .errors({
      'validation': {
        status: 400,
        data: z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
      }
    })
};
