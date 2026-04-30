export interface CreateTaskRequest {
  description?: string;
  priority: "high" | "low" | "medium";
  title: string;
}

export interface Task {
  description?: string;
  done: boolean;
  id: number;
  priority: "high" | "low" | "medium";
  title: string;
}
