import { BaklavaClient, BaklavaHttpError } from "./client";
import type * as T from "./types";

/** List tasks — List all tasks in a project */
export async function listTasks(client: BaklavaClient, params: {
  projectId: number;
}): Promise<T.Task[]> {
  const url = new URL(`${client.baseUrl}/projects/${encodeURIComponent(String(params.projectId))}/tasks`);
  let __ret!: T.Task[];
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
  body: T.CreateTaskRequest;
}): Promise<T.Task> {
  const url = new URL(`${client.baseUrl}/projects/${encodeURIComponent(String(params.projectId))}/tasks`);
  let __ret!: T.Task;
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
