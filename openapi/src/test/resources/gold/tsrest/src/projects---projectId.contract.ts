import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const projectsProjectIdContract = initContract().router({
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
  }
});
