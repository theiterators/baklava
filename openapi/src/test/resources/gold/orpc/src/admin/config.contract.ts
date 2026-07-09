import { oc } from "@orpc/contract";
import { healthResponseSchema } from "../schemas";

export const adminConfig = {
  get: oc
    .route({
      method: 'GET',
      path: '/admin/config',
      summary: 'Get config',
      description: 'Read the effective runtime configuration',
      operationId: 'adminGetConfig',
      tags: ['Admin'],
      successStatus: 200,
      inputStructure: 'detailed',
      spec: (current) => ({ ...current, security: [{ basicAuth: [] }] })
    })
    .output(healthResponseSchema)
};
