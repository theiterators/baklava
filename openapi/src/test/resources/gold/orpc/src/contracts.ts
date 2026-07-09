import { adminConfig } from "./admin/config.contract";
import { adminLoggers } from "./admin/loggers.contract";
import { auth } from "./auth.contract";
import { health } from "./health.contract";
import { me } from "./me.contract";
import { projects } from "./projects.contract";
import { users } from "./users.contract";
import { webhooks } from "./webhooks.contract";

export const contracts = {
  admin: {
    config: adminConfig,
    loggers: adminLoggers
  },
  auth,
  health,
  me,
  projects,
  users,
  webhooks
};
