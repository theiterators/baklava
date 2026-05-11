import type { User } from "../common/types";

export interface PaginatedUsers {
  limit: number;
  page: number;
  total: number;
  users: User[];
}

export interface PhotoUpload {
  id: string;
  variants: Record<string, PhotoVariant>;
}

export interface PhotoVariant {
  format: string;
  height: number;
  url: string;
  width: number;
}

export interface UpdateUserRequest {
  name: string;
  role: "admin" | "guest" | "member";
}
