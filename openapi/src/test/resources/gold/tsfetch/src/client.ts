/** API client configuration. Instantiate once and pass to every generated endpoint function. */
export interface BaklavaClientConfig {
  baseUrl: string;
  fetch?: typeof fetch;
  bearerToken?: string;
  basic?: { username: string; password: string };
  apiKeys?: Record<string, string>;
}

export class BaklavaClient {
  readonly baseUrl: string;
  readonly fetch: typeof fetch;
  readonly bearerToken?: string;
  readonly basic?: { username: string; password: string };
  readonly apiKeys?: Record<string, string>;

  constructor(config: BaklavaClientConfig) {
    this.baseUrl     = config.baseUrl.replace(/\/+$/, "");
    this.fetch       = config.fetch ?? (globalThis.fetch?.bind(globalThis) as typeof fetch);
    this.bearerToken = config.bearerToken;
    this.basic       = config.basic;
    this.apiKeys     = config.apiKeys;
  }

  authHeaders(): Record<string, string> {
    const h: Record<string, string> = {};
    if (this.bearerToken) h["Authorization"] = `Bearer ${this.bearerToken}`;
    else if (this.basic)  h["Authorization"] = `Basic ${btoa(`${this.basic.username}:${this.basic.password}`)}`;
    return h;
  }
}

export class BaklavaHttpError extends Error {
  constructor(public readonly status: number, public readonly body: string, message?: string) {
    super(message ?? `HTTP ${status}: ${body}`);
    this.name = "BaklavaHttpError";
  }
}
