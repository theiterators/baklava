import { BaklavaClient, BaklavaHttpError } from "./client";
import type * as T from "./types";

/** Login — Exchange HTTP Basic credentials for a JWT token */
export async function login(client: BaklavaClient, params: {
  body: T.LoginForm;
}): Promise<T.LoginResponse> {
  const url = new URL(`${client.baseUrl}/auth/login`);
  let __ret!: T.LoginResponse;
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

/** Who am I — Return the profile of the currently authenticated user */
export async function me(_client: BaklavaClient): Promise<T.User> {
  const url = new URL(`${client.baseUrl}/me`);
  let __ret!: T.User;
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
