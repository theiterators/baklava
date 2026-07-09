import { z } from "zod";
import { oc } from "@orpc/contract";
import { projectSchema } from "./schemas";

export const projectsProjectIdContract = {
  patch: oc
    .route({
      method: 'PATCH',
      path: '/projects/{projectId}',
      summary: 'Patch project',
      description: 'Partially update a project',
      operationId: 'patchProject',
      tags: ['Projects'],
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
    .output(projectSchema)
};
