import { z } from "zod";
import { initContract } from "@ts-rest/core";
import { healthResponseSchema } from "../schemas";

export const adminConfig = initContract().router({
  get: {
    summary: 'Get config',
    description: 'Read the effective runtime configuration\n\nRequires authentication: basicAuth (HTTP Basic).',
    method: 'GET',
    path: '/admin/config',
    responses: {
      200: healthResponseSchema
    }
  }
});
