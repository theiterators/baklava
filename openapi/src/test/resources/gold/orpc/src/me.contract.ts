import { z } from "zod";
import { oc } from "@orpc/contract";

export const meContract = {
  get: oc
    .route({
      method: 'GET',
      path: '/me',
      summary: 'Who am I',
      description: 'Return the profile of the currently authenticated user',
      successStatus: 200,
      inputStructure: 'detailed'
    })
    .output(z.object({
        "email": z.string(),
        "id": z.string().uuid(),
        "name": z.string(),
        "role": z.enum(["admin","guest","member"]).describe("User role within the system")}))
};
