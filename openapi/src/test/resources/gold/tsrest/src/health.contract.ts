import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { healthResponseSchema } from "./schemas";

export const health = initContract().router({
  get: {
    summary: 'Liveness probe',
    description: 'Return service liveness — no authentication required',
    method: 'GET',
    path: '/health',
    responses: {
      200: healthResponseSchema
    }
  }
});
