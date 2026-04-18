import { z } from "zod";
import { initContract } from "@ts-rest/core";

export const usersUserIdAvatarContract = initContract().router({
  put: {
    summary: 'Upload avatar',
    description: 'Upload or replace the user\'s avatar image',
    method: 'PUT',
    path: '/users/:userId/avatar',
    pathParams: z.object({userId: z.string().uuid()}),
    headers: z.object({"Content-Type": z.string()}),
    body: z.string(),
    responses: {
      204: z.undefined()
    }
  }
});
