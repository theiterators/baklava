import { z } from "zod";

export const createProjectRequestSchema = z.object({
        "description": z.string().nullish(),
        "name": z.string(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")});
export type CreateProjectRequest = z.infer<typeof createProjectRequestSchema>;

export const createTaskRequestSchema = z.object({
        "description": z.string().nullish(),
        "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
        "title": z.string()});
export type CreateTaskRequest = z.infer<typeof createTaskRequestSchema>;

export const patchProjectRequestSchema = z.object({
        "description": z.string().nullish(),
        "name": z.string().nullish(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()});
export type PatchProjectRequest = z.infer<typeof patchProjectRequestSchema>;

export const projectSchema = z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.string().uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")});
export type Project = z.infer<typeof projectSchema>;

export const taskSchema = z.object({
        "description": z.string().nullish(),
        "done": z.boolean(),
        "id": z.number().int(),
        "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
        "title": z.string()});
export type Task = z.infer<typeof taskSchema>;
