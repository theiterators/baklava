import { createORPCClient, ORPCError } from "@orpc/client";
import type { ContractRouterClient } from "@orpc/contract";
import type { JsonifiedClient } from "@orpc/openapi-client";
import { OpenAPILink } from "@orpc/openapi-client/fetch";
import { contracts } from "./contracts";

const ERROR_CODE_FIELD = "code";

export type ContractsClient = JsonifiedClient<ContractRouterClient<typeof contracts>>;

export interface CreateContractsClientOptions {
  fetch?: typeof globalThis.fetch;
  headers?: Record<string, string> | (() => Record<string, string>);
}

export function createContractsClient(
  url: string,
  options: CreateContractsClientOptions = {},
): ContractsClient {
  const link = new OpenAPILink(contracts, {
    url,
    fetch: options.fetch,
    headers: options.headers,
    customErrorResponseBodyDecoder: (body, response) => {
      if (body === null || typeof body !== "object") return null;
      const record = body as Record<string, unknown>;
      const code = record[ERROR_CODE_FIELD];
      if (typeof code !== "string") return null;
      const title = record["title"];
      return new ORPCError(code, {
        status: response.status,
        message: typeof title === "string" ? title : code,
        data: body,
        defined: true,
      });
    },
  });
  return createORPCClient(link);
}
