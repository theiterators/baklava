import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const projects = initContract().router({
  get: {
    summary: 'List projects',
    description: 'List projects, optionally filtered by status',
    method: 'GET',
    path: '/projects',
    query: z.object({status: z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()}),
    responses: {
      200: z.array(z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.string().uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")}))
    }
  },
  post: {
    summary: 'Create project',
    description: 'Create a new project',
    method: 'POST',
    path: '/projects',
    body: z.object({
        "description": z.string().nullish(),
        "name": z.string(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")}),
    responses: {
      201: z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.string().uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")}),
      400: z.object({
        "code": z.string(),
        "details": z.array(z.string()).nullish(),
        "message": z.string()})
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
        200: z.object({
          "createdAt": z.string(),
          "description": z.string().nullish(),
          "id": z.number().int(),
          "name": z.string(),
          "ownerId": z.string().uuid(),
          "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")})
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
          200: z.array(z.object({
            "description": z.string().nullish(),
            "done": z.boolean(),
            "id": z.number().int(),
            "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
            "title": z.string()}))
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
          201: z.object({
            "description": z.string().nullish(),
            "done": z.boolean(),
            "id": z.number().int(),
            "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
            "title": z.string()})
        }
      }
    }
  }
});
