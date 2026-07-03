import { z } from "zod";
import { oc } from "@orpc/contract";

export const projectsProjectIdTasksContract = {
  get: oc
    .route({
      method: 'GET',
      path: '/projects/{projectId}/tasks',
      summary: 'List tasks',
      description: 'List all tasks in a project',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({projectId: z.number().int()})
    }))
    .output(z.array(z.object({
        "description": z.string().nullish(),
        "done": z.boolean(),
        "id": z.number().int(),
        "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
        "title": z.string()}))),
  post: oc
    .route({
      method: 'POST',
      path: '/projects/{projectId}/tasks',
      summary: 'Create task',
      description: 'Create a task in a project',
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
    .output(z.object({
        "description": z.string().nullish(),
        "done": z.boolean(),
        "id": z.number().int(),
        "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
        "title": z.string()}))
};
