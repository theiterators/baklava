import { z } from "zod";
import { oc } from "@orpc/contract";

export const usersUserIdPhotoContract = {
  post: oc
    .route({
      method: 'POST',
      path: '/users/{userId}/photo',
      summary: 'Upload photo',
      description: 'Upload a profile photo alongside a caption as multipart/form-data',
      successStatus: 201,
      inputStructure: 'detailed'
    })
    .input(z.object({
      params: z.object({userId: z.string().uuid()}),
      body: z.object({caption: z.string(), photo: z.instanceof(File)})
    }))
    .output(z.object({
        "id": z.string().uuid(),
        "variants": z.record(z.string(), z.object({
        "format": z.string(),
        "height": z.number().int(),
        "url": z.string(),
        "width": z.number().int()}))}))
};
