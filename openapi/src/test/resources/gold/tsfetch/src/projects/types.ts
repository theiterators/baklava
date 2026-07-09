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
