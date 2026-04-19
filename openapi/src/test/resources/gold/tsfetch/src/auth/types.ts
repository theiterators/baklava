import type { User } from "../common/types";

export interface LoginForm {
  client_id: string;
  grant_type: string;
}

export interface LoginResponse {
  token: string;
  user: User;
}
