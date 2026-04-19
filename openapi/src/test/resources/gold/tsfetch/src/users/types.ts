import type { User } from "../common/types";

export interface PaginatedUsers {
  limit: number;
  page: number;
  total: number;
  users: User[];
}

export interface UpdateUserRequest {
  name: string;
  role: "admin" | "guest" | "member";
}
