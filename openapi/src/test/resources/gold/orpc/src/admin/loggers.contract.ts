import { z } from "zod";
import { oc } from "@orpc/contract";
import { healthResponseSchema } from "../schemas";

export const adminLoggers = {
  byName: {
    get: oc
      .route({
        method: 'GET',
        path: '/admin/loggers/{name}',
        summary: 'Get logger level',
        description: 'Read a logger\'s effective level',
        operationId: 'adminGetLogger',
        tags: ['Admin'],
        successStatus: 200,
        inputStructure: 'detailed'
      })
      .input(z.object({
        params: z.object({name: z.string()})
      }))
      .output(healthResponseSchema)
  }
};
