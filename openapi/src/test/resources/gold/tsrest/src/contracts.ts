import { initContract } from "@ts-rest/core";
import { auth } from "./auth.contract";
import { health } from "./health.contract";
import { me } from "./me.contract";
import { projects } from "./projects.contract";
import { users } from "./users.contract";
import { webhooks } from "./webhooks.contract";

export const contracts = initContract().router({
  auth,
  health,
  me,
  projects,
  users,
  webhooks
});
