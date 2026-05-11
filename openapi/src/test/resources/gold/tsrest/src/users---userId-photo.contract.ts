import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const usersUserIdPhotoContract = initContract().router({
  post: {
    summary: 'Upload photo',
    description: 'Upload a profile photo alongside a caption as multipart/form-data',
    method: 'POST',
    path: '/users/:userId/photo',
    pathParams: z.object({userId: z.string().uuid()}),
    contentType: 'multipart/form-data',
    body: z.object({caption: z.string(), photo: z.instanceof(File)}),
    responses: {
      201: z.object({
        "id": z.string().uuid(),
        "variants": z.record(z.string(), z.object({
        "format": z.string(),
        "height": z.number().int(),
        "url": z.string(),
        "width": z.number().int()}))})
    }
  }
});
