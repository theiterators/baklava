import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const healthContract = initContract().router({
  get: {
    summary: 'Liveness probe',
    description: 'Return service liveness — no authentication required',
    method: 'GET',
    path: '/health',
    responses: {
      200: z.object({
        "status": z.string(),
        "uptimeSeconds": z.number().int()})
    }
  }
});
