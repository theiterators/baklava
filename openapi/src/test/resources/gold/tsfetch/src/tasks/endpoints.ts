import { BaklavaClient, BaklavaHttpError } from "../client";
import type { CreateTaskRequest, Task } from "./types";

/** List tasks — List all tasks in a project */
export async function listTasks(client: BaklavaClient, params: {
  projectId: number;
}): Promise<Task[]> {
  const url = new URL(`${client.baseUrl}/projects/${encodeURIComponent(String(params.projectId))}/tasks`);
  let __ret!: Task[];
  const res = await client.fetch(url.toString(), {
    method: "GET",
    headers: {
    ...client.authHeaders(),
    },
  });
  const text = await res.text();
  if (!res.ok) throw new BaklavaHttpError(res.status, text);
  return (text ? JSON.parse(text) : undefined) as typeof __ret;
}

/** Create task — Create a task in a project */
export async function createTask(client: BaklavaClient, params: {
  projectId: number;
  body: CreateTaskRequest;
}): Promise<Task> {
  const url = new URL(`${client.baseUrl}/projects/${encodeURIComponent(String(params.projectId))}/tasks`);
  let __ret!: Task;
  const res = await client.fetch(url.toString(), {
    method: "POST",
    headers: {
    ...client.authHeaders(),
    "Content-Type": "application/json",
    },
    body: JSON.stringify(params.body),
  });
  const text = await res.text();
  if (!res.ok) throw new BaklavaHttpError(res.status, text);
  return (text ? JSON.parse(text) : undefined) as typeof __ret;
}
