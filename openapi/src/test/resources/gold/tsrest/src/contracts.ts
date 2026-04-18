import { authLoginContract } from "./auth-login.contract";
import { healthContract } from "./health.contract";
import { meContract } from "./me.contract";
import { projectsContract } from "./projects.contract";
import { projectsProjectIdContract } from "./projects---projectId.contract";
import { projectsProjectIdTasksContract } from "./projects---projectId-tasks.contract";
import { usersContract } from "./users.contract";
import { usersUserIdContract } from "./users---userId.contract";
import { usersUserIdAvatarContract } from "./users---userId-avatar.contract";
import { webhooksContract } from "./webhooks.contract";

export const contracts: {
  "auth-login": typeof authLoginContract;
  "health": typeof healthContract;
  "me": typeof meContract;
  "projects": typeof projectsContract;
  "projects---projectId": typeof projectsProjectIdContract;
  "projects---projectId-tasks": typeof projectsProjectIdTasksContract;
  "users": typeof usersContract;
  "users---userId": typeof usersUserIdContract;
  "users---userId-avatar": typeof usersUserIdAvatarContract;
  "webhooks": typeof webhooksContract
} = {
  "auth-login": authLoginContract,
  "health": healthContract,
  "me": meContract,
  "projects": projectsContract,
  "projects---projectId": projectsProjectIdContract,
  "projects---projectId-tasks": projectsProjectIdTasksContract,
  "users": usersContract,
  "users---userId": usersUserIdContract,
  "users---userId-avatar": usersUserIdAvatarContract,
  "webhooks": webhooksContract
};

