/** API client configuration. Instantiate once and pass to every generated endpoint function. */
export interface BaklavaClientConfig {
  /** Base URL for every request, e.g. "https://api.example.com". No trailing slash. */
  baseUrl: string;
  /** Override for `fetch`. Defaults to `globalThis.fetch` — override for Node < 18 or to inject instrumentation. */
  fetch?: typeof fetch;
  /** Bearer / OAuth / OpenID Connect token used by any scheme that sends `Authorization: Bearer <token>`. */
  bearerToken?: string;
  /** HTTP Basic credentials. */
  basic?: { username: string; password: string };
  /** API key values keyed by the scheme's declared header / query / cookie name. */
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

  /** Headers contributed by any declared scheme that writes into `Authorization` or an API key header. Schemes that put
   *  credentials in query string or cookie produce no header here — the generated endpoint functions set those inline.
   */
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
