/** API client configuration. Instantiate once and pass to every generated endpoint function. */
export interface BaklavaClientConfig {
  baseUrl: string;
  fetch?: typeof fetch;
  bearerToken?: string;
  basic?: { username: string; password: string };
  apiKeys?: Record<string, string>;
}

function resolveFetch(configured?: typeof fetch): typeof fetch {
  if (configured) return configured;
  const g = globalThis.fetch;
  if (g) return g.bind(globalThis) as typeof fetch;
  throw new Error(
    "BaklavaClient: no fetch implementation available. " +
    "Pass `fetch` in BaklavaClientConfig (e.g. node-fetch or undici) on Node < 18."
  );
}

function b64Encode(raw: string): string {
  const g = globalThis as { btoa?: (s: string) => string; Buffer?: { from(s: string, enc: string): { toString(enc: string): string } } };
  if (g.btoa) return g.btoa(raw);
  if (g.Buffer) return g.Buffer.from(raw, "utf-8").toString("base64");
  throw new Error("BaklavaClient: no base64 encoder available (btoa/Buffer).");
}

export class BaklavaClient {
  readonly baseUrl: string;
  readonly fetch: typeof fetch;
  readonly bearerToken?: string;
  readonly basic?: { username: string; password: string };
  readonly apiKeys?: Record<string, string>;

  constructor(config: BaklavaClientConfig) {
    this.baseUrl     = config.baseUrl.replace(/\/+$/, "");
    this.fetch       = resolveFetch(config.fetch);
    this.bearerToken = config.bearerToken;
    this.basic       = config.basic;
    this.apiKeys     = config.apiKeys;
  }

  authHeaders(): Record<string, string> {
    const h: Record<string, string> = {};
    if (this.bearerToken) h["Authorization"] = `Bearer ${this.bearerToken}`;
    else if (this.basic)  h["Authorization"] = `Basic ${b64Encode(`${this.basic.username}:${this.basic.password}`)}`;
    return h;
  }
}

export class BaklavaHttpError extends Error {
  constructor(public readonly status: number, public readonly body: string, message?: string) {
    super(message ?? `HTTP ${status}: ${body}`);
    this.name = "BaklavaHttpError";
  }
}
