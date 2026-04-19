export interface CreateProjectRequest {
  description?: string;
  name: string;
  status: "active" | "archived" | "draft";
}

export interface CreateTaskRequest {
  description?: string;
  priority: "high" | "low" | "medium";
  title: string;
}

export interface ErrorResponse {
  code: string;
  details?: string[];
  message: string;
}

export interface HealthResponse {
  status: string;
  uptimeSeconds: number;
}

export interface LoginForm {
  client_id: string;
  grant_type: string;
}

export interface LoginResponse {
  token: string;
  user: T.User;
}

export interface PaginatedUsers {
  limit: number;
  page: number;
  total: number;
  users: T.User[];
}

export interface PatchProjectRequest {
  description?: string;
  name?: string;
  status?: "active" | "archived" | "draft";
}

export interface Project {
  createdAt: string;
  description?: string;
  id: number;
  name: string;
  ownerId: string;
  status: "active" | "archived" | "draft";
}

export interface Task {
  description?: string;
  done: boolean;
  id: number;
  priority: "high" | "low" | "medium";
  title: string;
}

export interface UpdateUserRequest {
  name: string;
  role: "admin" | "guest" | "member";
}

export interface User {
  email: string;
  id: string;
  name: string;
  role: "admin" | "guest" | "member";
}

export interface WebhookAck {
  received: boolean;
}

export interface WebhookPayload {
  data: string;
  event: string;
}
