import { oc } from "@orpc/contract";
import { userSchema } from "./schemas";

export const me = {
  get: oc
    .route({
      method: 'GET',
      path: '/me',
      summary: 'Who am I',
      description: 'Return the profile of the currently authenticated user',
      operationId: 'me',
      tags: ['Auth'],
      successStatus: 200,
      inputStructure: 'detailed',
      spec: (current) => ({ ...current, security: [{ bearerAuth: [] }] })
    })
    .output(userSchema)
};
