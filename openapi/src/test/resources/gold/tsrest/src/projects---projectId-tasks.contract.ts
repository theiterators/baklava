import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const projectsProjectIdTasksContract = initContract().router({
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
});
