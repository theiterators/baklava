import { BaklavaClient, BaklavaHttpError } from "../../client";
import type { HealthResponse } from "../../common/types";

/** Get config — Read the effective runtime configuration */
export async function adminGetConfig(client: BaklavaClient): Promise<HealthResponse> {
  const url = new URL(`${client.baseUrl}/admin/config`);
  let __ret!: HealthResponse;
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
