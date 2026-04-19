import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const usersUserIdPhotoContract = initContract().router({
  post: {
    summary: 'Upload photo',
    description: 'Upload a profile photo alongside a caption as multipart/form-data',
    method: 'POST',
    path: '/users/:userId/photo',
    pathParams: z.object({userId: z.string().uuid()}),
    body: z.object({}),
    responses: {
      204: z.undefined()
    }
  }
});
