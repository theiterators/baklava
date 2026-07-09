import { z } from "zod";
import { oc } from "@orpc/contract";
import { taskSchema } from "./schemas";

export const projectsProjectIdTasksContract = {
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
};
