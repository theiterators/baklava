import { BaklavaClient, BaklavaHttpError } from "../client";
import type { CreateProjectRequest, PatchProjectRequest, Project } from "./types";
import type { ErrorResponse } from "../common/types";

/** List projects — List projects, optionally filtered by status */
export async function listProjects(client: BaklavaClient, params?: {
  status?: "active" | "archived" | "draft";
}): Promise<Project[]> {
  const url = new URL(`${client.baseUrl}/projects`);
  if (params?.status !== undefined) url.searchParams.set("status", String(params.status));
  let __ret!: Project[];
  const res = await client.fetch(url.toString(), {
    method: "GET",
    headers: {
    ...client.authHeaders(),
    },
  });
  const text = await res.text();
  if (!res.ok) throw new BaklavaHttpError(res.status, text);
  const ct = res.headers.get("content-type") ?? "";
  if (ct.includes("application/json")) {
    return (text ? JSON.parse(text) : undefined) as typeof __ret;
  }
  return text as unknown as typeof __ret;
}

/** Create project — Create a new project */
export async function createProject(client: BaklavaClient, params: {
  body: CreateProjectRequest;
}): Promise<Project> {
  const url = new URL(`${client.baseUrl}/projects`);
  let __ret!: Project;
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
  const ct = res.headers.get("content-type") ?? "";
  if (ct.includes("application/json")) {
    return (text ? JSON.parse(text) : undefined) as typeof __ret;
  }
  return text as unknown as typeof __ret;
}

/** Patch project — Partially update a project */
export async function patchProject(client: BaklavaClient, params: {
  projectId: number;
  body: PatchProjectRequest;
}): Promise<Project> {
  const url = new URL(`${client.baseUrl}/projects/${encodeURIComponent(String(params.projectId))}`);
  let __ret!: Project;
  const res = await client.fetch(url.toString(), {
    method: "PATCH",
    headers: {
    ...client.authHeaders(),
    "Content-Type": "application/json",
    },
    body: JSON.stringify(params.body),
  });
  const text = await res.text();
  if (!res.ok) throw new BaklavaHttpError(res.status, text);
  const ct = res.headers.get("content-type") ?? "";
  if (ct.includes("application/json")) {
    return (text ? JSON.parse(text) : undefined) as typeof __ret;
  }
  return text as unknown as typeof __ret;
}
