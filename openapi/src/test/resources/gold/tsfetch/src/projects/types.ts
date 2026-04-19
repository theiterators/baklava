export interface CreateProjectRequest {
  description?: string;
  name: string;
  status: "active" | "archived" | "draft";
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
