import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { healthResponseSchema } from "../schemas";

export const adminConfig = initContract().router({
  get: {
    summary: 'Get config',
    description: 'Read the effective runtime configuration',
    method: 'GET',
    path: '/admin/config',
    responses: {
      200: healthResponseSchema
    }
  }
});
