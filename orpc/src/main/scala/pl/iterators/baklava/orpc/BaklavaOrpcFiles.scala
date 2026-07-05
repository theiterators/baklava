package pl.iterators.baklava.orpc

object BaklavaOrpcFiles {

  val files = List(
    (
      "package.json",
      """
        |{
        |  "name": "my-contracts",
        |  "version": "1.0.0",
        |  "description": "oRPC contract package",
        |  "main": "dist/index.js",
        |  "types": "dist/index.d.ts",
        |  "exports": {
        |    ".": {
        |      "import": "./dist/index.js",
        |      "types": "./dist/index.d.ts"
        |    }
        |  },
        |  "files": [
        |    "dist"
        |  ],
        |  "scripts": {
        |    "build:js": "esbuild src/contracts.ts src/client.ts --bundle --platform=node --target=es2022 --format=esm --tree-shaking=true --external:@orpc/contract --external:@orpc/client --external:@orpc/openapi-client --external:zod --outdir=dist",
        |    "build:dts": "dts-bundle-generator -o dist/index.d.ts src/contracts.ts",
        |    "build:package": "cp package-contracts.json dist/package.json && sed -i \"s/VERSION/${VERSION}/g\" dist/package.json",
        |    "build": "pnpm run build:js && pnpm run build:dts && pnpm run build:package"
        |  },
        |  "peerDependencies": {
        |    "@orpc/contract": "^1.14.6",
        |    "@orpc/client": "^1.14.6",
        |    "@orpc/openapi-client": "^1.14.6",
        |    "zod": "^4.0.0"
        |  },
        |  "devDependencies": {
        |    "esbuild": "^0.25.2",
        |    "dts-bundle-generator": "^9.5.1",
        |    "typescript": "^5.8.3"
        |  }
        |}
        |""".stripMargin
    ),
    (
      "tsconfig.json",
      """
        |{
        |  "compilerOptions": {
        |    "target": "ES2022",
        |    "module": "ES2022",
        |    "declaration": true,
        |    "declarationMap": false,
        |    "emitDeclarationOnly": true,
        |    "strict": true,
        |    "moduleResolution": "bundler",
        |    "esModuleInterop": true,
        |    "baseUrl": "./src",
        |    "outDir": "dist"
        |  },
        |  "include": ["src/*.ts"]
        |}
        |""".stripMargin
    )
  )

  /** The ready-made client: an OpenAPILink whose error decoder lifts the backend's discriminated error bodies (RFC 9457 `type` by default)
    * into defined ORPCErrors under the same codes the contracts declare via `.errors(...)` — so `isDefinedError` narrows end to end.
    */
  def clientTs(errorCodeField: String): String = {
    val field = errorCodeField.replace("\\", "\\\\").replace("\"", "\\\"")
    s"""import { createORPCClient, ORPCError } from "@orpc/client";
       |import type { ContractRouterClient } from "@orpc/contract";
       |import type { JsonifiedClient } from "@orpc/openapi-client";
       |import { OpenAPILink } from "@orpc/openapi-client/fetch";
       |import { contracts } from "./contracts";
       |
       |const ERROR_CODE_FIELD = "$field";
       |
       |export type ContractsClient = JsonifiedClient<ContractRouterClient<typeof contracts>>;
       |
       |export interface CreateContractsClientOptions {
       |  fetch?: typeof globalThis.fetch;
       |  headers?: Record<string, string> | (() => Record<string, string>);
       |}
       |
       |export function createContractsClient(
       |  url: string,
       |  options: CreateContractsClientOptions = {},
       |): ContractsClient {
       |  const link = new OpenAPILink(contracts, {
       |    url,
       |    fetch: options.fetch,
       |    headers: options.headers,
       |    customErrorResponseBodyDecoder: (body, response) => {
       |      if (body === null || typeof body !== "object") return null;
       |      const record = body as Record<string, unknown>;
       |      const code = record[ERROR_CODE_FIELD];
       |      if (typeof code !== "string") return null;
       |      const title = record["title"];
       |      return new ORPCError(code, {
       |        status: response.status,
       |        message: typeof title === "string" ? title : code,
       |        data: body,
       |        defined: true,
       |      });
       |    },
       |  });
       |  return createORPCClient(link);
       |}
       |""".stripMargin
  }
}
