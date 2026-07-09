import { z } from "zod";
import { oc } from "@orpc/contract";

export const usersUserIdPhotoContract = {
  post: oc
    .route({
      method: 'POST',
      path: '/users/{userId}/photo',
      summary: 'Upload photo',
      description: 'Upload a profile photo alongside a caption as multipart/form-data',
      operationId: 'uploadPhoto',
      tags: ['Users'],
      successStatus: 201,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.uuid()}),
      body: z.object({caption: z.string(), photo: z.file()})
    }))
    .output(z.object({
        "id": z.uuid(),
        "variants": z.record(z.string(), z.object({
        "format": z.string(),
        "height": z.number().int(),
        "url": z.string(),
        "width": z.number().int()}))}))
};
