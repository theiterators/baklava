import { z } from "zod";
import { oc } from "@orpc/contract";

export const projectsProjectIdContract = {
  patch: oc
    .route({
      method: 'PATCH',
      path: '/projects/{projectId}',
      summary: 'Patch project',
      description: 'Partially update a project',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({projectId: z.number().int()}),
      body: z.object({
        "description": z.string().nullish(),
        "name": z.string().nullish(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project").nullish()})
    }))
    .output(z.object({
        "createdAt": z.string(),
        "description": z.string().nullish(),
        "id": z.number().int(),
        "name": z.string(),
        "ownerId": z.string().uuid(),
        "status": z.enum(["active","archived","draft"]).describe("Lifecycle state of a project")}))
};
