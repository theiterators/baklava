import { BaklavaClient, BaklavaHttpError } from "../client";
import type { PaginatedUsers, UpdateUserRequest } from "./types";
import type { ErrorResponse, User } from "../common/types";

/** List users — List users with pagination and optional role filter */
export async function listUsers(client: BaklavaClient, params?: {
  page?: number;
  limit?: number;
  role?: "admin" | "guest" | "member";
}): Promise<PaginatedUsers> {
  const url = new URL(`${client.baseUrl}/users`);
  if (params?.page !== undefined) url.searchParams.set("page", String(params.page));
  if (params?.limit !== undefined) url.searchParams.set("limit", String(params.limit));
  if (params?.role !== undefined) url.searchParams.set("role", String(params.role));
  let __ret!: PaginatedUsers;
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

/** Delete user — Delete a user */
export async function deleteUser(client: BaklavaClient, params: {
  userId: string;
}): Promise<void> {
  const url = new URL(`${client.baseUrl}/users/${encodeURIComponent(String(params.userId))}`);
  const res = await client.fetch(url.toString(), {
    method: "DELETE",
    headers: {
    ...client.authHeaders(),
    },
  });
  if (!res.ok) throw new BaklavaHttpError(res.status, await res.text());
}

/** Get user — Fetch a single user by UUID */
export async function getUser(client: BaklavaClient, params: {
  userId: string;
}): Promise<User> {
  const url = new URL(`${client.baseUrl}/users/${encodeURIComponent(String(params.userId))}`);
  let __ret!: User;
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

/** Update user — Replace a user's profile (admin only) */
export async function updateUser(client: BaklavaClient, params: {
  userId: string;
  body: UpdateUserRequest;
}): Promise<User> {
  const url = new URL(`${client.baseUrl}/users/${encodeURIComponent(String(params.userId))}`);
  let __ret!: User;
  const res = await client.fetch(url.toString(), {
    method: "PUT",
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

/** Upload photo — Upload a profile photo alongside a caption as multipart/form-data */
export async function uploadPhoto(client: BaklavaClient, params: {
  userId: string;
  body: Record<string, unknown>;
}): Promise<void> {
  const url = new URL(`${client.baseUrl}/users/${encodeURIComponent(String(params.userId))}/photo`);
  const res = await client.fetch(url.toString(), {
    method: "POST",
    headers: {
    ...client.authHeaders(),
    "Content-Type": "multipart/form-data; boundary=baklava-multipart-boundary",
    },
    body: params.body as unknown as BodyInit,
  });
  if (!res.ok) throw new BaklavaHttpError(res.status, await res.text());
}
