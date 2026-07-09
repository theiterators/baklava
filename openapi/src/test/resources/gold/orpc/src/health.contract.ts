import { z } from "zod";
import { oc } from "@orpc/contract";

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
    .output(z.object({
        "status": z.string(),
        "uptimeSeconds": z.number().int()}))
};
