export interface CreateProjectRequest {
  description?: string | null;
  name: string;
  status: "active" | "archived" | "draft";
}

export interface CreateTaskRequest {
  description?: string | null;
  priority: "high" | "low" | "medium";
  title: string;
}

export interface PatchProjectRequest {
  description?: string | null;
  name?: string | null;
  status?: "active" | "archived" | "draft" | null;
}

export interface Project {
  createdAt: string;
  description?: string | null;
  id: number;
  name: string;
  ownerId: string;
  status: "active" | "archived" | "draft";
}

export interface Task {
  description?: string | null;
  done: boolean;
  id: number;
  priority: "high" | "low" | "medium";
  title: string;
}
