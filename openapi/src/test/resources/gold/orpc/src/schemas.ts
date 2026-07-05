import { z } from "zod";

export const createProjectRequestSchema = z.object({
        "description": z.string().nullish(),
        "name": z.string(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")});

export const loginFormSchema = z.object({
        "client_id": z.string(),
        "grant_type": z.string()});

export const projectSchema = z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")});

export const taskSchema = z.object({
        "description": z.string().nullish(),
        "done": z.boolean(),
        "id": z.number().int(),
        "priority": z.enum(["high","low","medium"]).describe("Task priority level"),
        "title": z.string()});

export const userSchema = z.object({
        "email": z.string(),
        "id": z.uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")});

export const webhookPayloadSchema = z.object({
        "data": z.string(),
        "event": z.string()});
