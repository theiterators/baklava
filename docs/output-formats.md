---
sidebar_position: 6
title: Output Formats
---

# Output Formats

Baklava supports four output formats. You can use one or more simultaneously — each is an independent SBT dependency that produces its own output in `target/baklava/`.

## How It Works

Formatters are **automatically discovered** via reflection. Any formatter on the test classpath is picked up and run — no registration or configuration needed (beyond format-specific config like `openapi-info`). Just add the dependency and it works:

```scala
libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-simple"  % "VERSION" % Test,  // adds Simple format
  "pl.iterators" %% "baklava-openapi" % "VERSION" % Test,  // adds OpenAPI format
  "pl.iterators" %% "baklava-tsrest"  % "VERSION" % Test,  // adds TS-REST format
  "pl.iterators" %% "baklava-tsfetch" % "VERSION" % Test   // adds TypeScript fetch client
)
```

The generation pipeline:
1. During `sbt test`, each test case is serialized to a JSON file in `target/baklava/calls/`
2. After tests complete, the SBT plugin runs `BaklavaGenerate` which reads all call files
3. Every formatter found on the classpath processes the calls and writes its output
4. The call files are cleaned up

## Simple Format

**Dependency:** `"pl.iterators" %% "baklava-simple" % "VERSION" % Test`
**Configuration:** None required
**Output:** `target/baklava/simple/`

Generates self-contained HTML files you can open in any browser:

- `index.html` — navigation page listing all endpoints (method + route), linking to individual pages
- One HTML file per endpoint, named by method and path (e.g., `GET__user__username__.html`)

Each endpoint page contains an HTML table with:
- Method, route, summary, description
- Authentication schemes (if any)
- Headers, path parameters, query parameters (with types and required indicators)
- Status codes from test cases
- Request body examples (JSON pretty-printed)
- Request body schema (JSON Schema Draft 7)
- Response body examples per status code
- Response body schema per status code

This is the simplest format to get started — no configuration, no external tools needed.

## OpenAPI Format

**Dependency:** `"pl.iterators" %% "baklava-openapi" % "VERSION" % Test`
**Configuration:** Required — `openapi-info` key in `baklavaGenerateConfigs`
**Output:** `target/baklava/openapi/openapi.yml`

Generates a single OpenAPI 3.0.1 YAML specification file containing:

- **Paths** organized by route and HTTP method, each with:
  - `operationId`, `summary`, `description`, `tags`
  - Parameters (query, path, header) with schemas, types, required flags, enum values
  - Request body with media type, schema, and multiple examples from different test cases
  - Responses grouped by status code, each with schema, examples, and response headers
- **Security schemes** auto-detected from your test cases (bearer, basic, API key, OAuth2, OpenID Connect, mutual TLS)
- **Components** section with all referenced security scheme definitions

When multiple test cases cover the same endpoint with different inputs/outputs, they appear as separate examples in the OpenAPI spec.

### Configuration

```scala
baklavaGenerateConfigs := Map(
  "openapi-info" ->
    s"""
      |openapi: 3.0.1
      |info:
      |  title: My API
      |  version: 1.0.0
      |""".stripMargin
)
```

The `openapi-info` value can be JSON or YAML and supports all OpenAPI info fields (title, version, description, contact, license, termsOfService).

### SwaggerUI (Pekko HTTP only)

Add `"pl.iterators" %% "baklava-pekko-http-routes" % "VERSION"` (not test-scoped) to serve the generated spec via SwaggerUI at runtime. See [Installation — SwaggerUI](installation.md#optional-swaggerui-support) for setup.

### Post-Processing

You can programmatically modify the generated OpenAPI spec by implementing `BaklavaOpenApiPostProcessor`. Implementations are discovered automatically via reflection — no registration needed. See [Configuration — Post-Processing](configuration.md#open-api-post-processing) for details.

## TypeScript REST (TS-REST) Format

**Dependency:** `"pl.iterators" %% "baklava-tsrest" % "VERSION" % Test`
**Configuration:** Required — `ts-rest-package-contract-json` key in `baklavaGenerateConfigs`
**Output:** `target/baklava/tsrest/`

Generates a complete TypeScript npm package using [ts-rest](https://ts-rest.com/) and [Zod](https://zod.dev/) for type-safe API contracts. The output can be published to npm or used as a local dependency in your frontend project.

### Generated Files

- `package.json` — npm package with build scripts, peer dependencies on `@ts-rest/core` and `zod`
- `tsconfig.json` — TypeScript configuration (ES2022, strict mode)
- `src/contracts.ts` — main exports file re-exporting all contracts
- `src/{name}.contract.ts` — one contract file per route group

### Contract Organization

Each unique path becomes its own contract file. The path is converted to a filename:
- `/` → `root.contract.ts`
- `/user/login` → `user-login.contract.ts`
- `/pet/{petId}` → `pet---petId.contract.ts`
- `/user/{id}/profile` → `user---id-profile.contract.ts`

Path parameters `{param}` are replaced with `--param`, dots with `---`, and segments are joined with `-`.

Endpoints sharing the same path but with different HTTP methods are grouped into one contract file. Each contract file exports a `ts-rest` router with typed endpoints.

### Zod Schema Mapping

Baklava schemas are converted to Zod validators:

| Baklava Schema | Zod Output |
|---|---|
| `String` | `z.string()` |
| `String` (email format) | `z.string().email()` |
| `String` (uuid format) | `z.string().uuid()` |
| `String` (date-time format) | `z.coerce.date()` |
| `String` (enum) | `z.enum(["val1", "val2"])` |
| `Int`, `Long` | `z.number().int()` |
| `Double`, `Float` | `z.number()` |
| `Boolean` | `z.boolean()` |
| `Seq[T]`, `List[T]` | `z.array(innerSchema)` |
| Case class | `z.object({ field: schema, ... })` |
| `Option[T]` | `schema.nullish()` |

When multiple test cases produce different schemas for the same endpoint input/output, they are combined into `z.union([...])`.

### Configuration

```scala
baklavaGenerateConfigs := Map(
  "ts-rest-package-contract-json" ->
    """
      |{
      |  "name": "@company/backend-contracts",
      |  "version": "1.0.0",
      |  "main": "index.js",
      |  "types": "index.d.ts"
      |}
      |""".stripMargin
)
```

### Usage in Frontend

After generating and building the package:

```bash
cd target/baklava/tsrest
pnpm install
pnpm run build
```

Then import in your TypeScript project:

```typescript
import { contracts } from "@company/backend-contracts";

// Full type safety and autocompletion for API calls
const userContract = contracts.user;
```

## TypeScript Fetch Client Format

**Dependency:** `"pl.iterators" %% "baklava-tsfetch" % "VERSION" % Test`
**Configuration:** Optional — `ts-fetch-package-json` key in `baklavaGenerateConfigs`
**Output:** `target/baklava/tsfetch/`

Generates a plain-TypeScript client library that uses the browser/Node `fetch` API — no ts-rest, zod, or other runtime dependencies. Every declared endpoint becomes a typed `async function` that accepts a `BaklavaClient` plus path/query/header/body parameters and returns a typed `Promise<T>` for the 2xx response body. Non-2xx responses throw `BaklavaHttpError`.

### Generated Files

- `package.json` / `tsconfig.json` — minimal npm package with a single `typescript` dev dep
- `src/client.ts` — `BaklavaClient` class with `baseUrl`, pluggable `fetch`, optional bearer/basic/API-key credentials; plus `BaklavaHttpError` for failed responses
- `src/common/types.ts` — interfaces for types used by two or more tags
- `src/{tag}/types.ts` — interfaces for types used only within that tag
- `src/{tag}/endpoints.ts` — one `async function` per endpoint in that tag. Untagged operations go into `src/default/endpoints.ts`.
- `src/index.ts` — re-exports every tag's endpoints. Per-tag types are re-exported under a namespace (`Users`, `Projects`, …) to avoid collisions; shared types appear under `Common`.

### Type Distribution

Each named schema is routed based on which tags' endpoints reference it:

- Used by **one tag** → `src/{tag}/types.ts`
- Used by **two or more tags** → `src/common/types.ts`

Endpoint files import types from the appropriate location (`./types`, `../common/types`, or `../{other-tag}/types`). Interface references inside other interfaces follow the same rule, so the output never duplicates a type.

### Schema Type Mapping

| Baklava Schema | TypeScript |
|---|---|
| `String` | `string` |
| `String` (enum) | `"val1" \| "val2"` |
| `Int`, `Long`, `Double`, `Float`, `BigDecimal` | `number` |
| `Boolean` | `boolean` |
| `Null` | `null` |
| `Seq[T]`, `List[T]`, `Vector[T]`, `Set[T]`, `Array[T]` | `InnerType[]` |
| Case class with properties | Named `interface` (re-exported per-tag as `Users.ClassName` / shared as `Common.ClassName`) |
| `Option[T]` | Field becomes optional (`field?: T`) |

### Configuration

Override the default `package.json` contents (name, version, dependencies, etc.) by supplying your own:

```scala
baklavaGenerateConfigs := Map(
  "ts-fetch-package-json" ->
    """
      |{
      |  "name": "@company/api-client",
      |  "version": "1.0.0",
      |  "type": "module",
      |  "main": "dist/index.js",
      |  "types": "dist/index.d.ts",
      |  "scripts": { "build": "tsc" },
      |  "devDependencies": { "typescript": "^5.4.0" }
      |}
      |""".stripMargin
)
```

Unset, a minimal default `package.json` is emitted.

### Usage in Frontend

After generation, build and import:

```bash
cd target/baklava/tsfetch
pnpm install && pnpm run build
```

```typescript
import { BaklavaClient, listUsers, createUser, Users, Common } from "@company/api-client";

const client = new BaklavaClient({
  baseUrl: "https://api.example.com",
  bearerToken: "jwt-token-here"
});

const page: Users.PaginatedUsers = await listUsers(client);
const newUser: Common.User = await createUser(client, { body: { name: "Alice" } });
```

### Caveats

- `BaklavaClient.authHeaders()` only materializes `Authorization` for bearer/basic/OAuth/OpenID Connect schemes. API-key-in-header schemes are injected per-endpoint based on `client.apiKeys`; API-key-in-query schemes go through `url.searchParams`; API-key-in-cookie schemes emit a `Cookie` header (which browsers may override for cross-origin requests).
- When an endpoint declares multiple 2xx responses with different body schemas, the return type is a `A | B` union of all distinct schemas. You can narrow at the call site with `typeof` / `in` checks.
- Responses are decoded as JSON only when the response `Content-Type` contains `application/json`. Any other content type falls through to the raw text (cast to the declared return type), so plain-text 2xx responses don't crash the parser.
- Request bodies are `JSON.stringify`d when the captured `requestContentType` is JSON (or unspecified). For captures with a non-JSON `requestContentType`, the body is passed through as `BodyInit` and the generator emits the captured `Content-Type` header — supply `FormData`, `Blob`, `URLSearchParams`, or a `string` at the call site.
