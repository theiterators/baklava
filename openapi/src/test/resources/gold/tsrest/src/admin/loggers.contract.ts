import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { healthResponseSchema } from "../schemas";

export const adminLoggers = initContract().router({
  byName: {
    get: {
      summary: 'Get logger level',
      description: 'Read a logger\'s effective level',
      method: 'GET',
      path: '/admin/loggers/:name',
      pathParams: z.object({name: z.string()}),
      responses: {
        200: healthResponseSchema
      }
    }
  }
});
