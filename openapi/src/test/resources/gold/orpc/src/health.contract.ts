import { oc } from "@orpc/contract";
import { healthResponseSchema } from "./schemas";

export const health = {
  get: oc
    .route({
      method: 'GET',
      path: '/health',
      summary: 'Liveness probe',
      description: 'Return service liveness — no authentication required',
      operationId: 'health',
      tags: ['System'],
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .output(healthResponseSchema)
};
