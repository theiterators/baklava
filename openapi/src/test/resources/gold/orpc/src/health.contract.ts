import { z } from "zod";
import { oc } from "@orpc/contract";

export const healthContract = {
  get: oc
    .route({
      method: 'GET',
      path: '/health',
      summary: 'Liveness probe',
      description: 'Return service liveness — no authentication required',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .output(z.object({
        "status": z.string(),
        "uptimeSeconds": z.number().int()}))
};
