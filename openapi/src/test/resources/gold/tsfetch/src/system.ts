import { BaklavaClient, BaklavaHttpError } from "./client";
import type * as T from "./types";

/** Liveness probe — Return service liveness — no authentication required */
export async function health(_client: BaklavaClient): Promise<T.HealthResponse> {
  const url = new URL(`${client.baseUrl}/health`);
  let __ret!: T.HealthResponse;
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
