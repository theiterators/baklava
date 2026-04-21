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
  "pl.iterators" %% "baklava-postman" % "VERSION" % Test   // adds Postman Collection format
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

## Postman Collection Format

**Dependency:** `"pl.iterators" %% "baklava-postman" % "VERSION" % Test`
**Configuration:** Optional — `postman.collectionName` key in `baklavaGenerateConfigs`
**Output:** `target/baklava/postman/collection.json`

Generates a [Postman Collection v2.1](https://schema.getpostman.com/json/collection/v2.1.0/collection.json) JSON document. The file imports cleanly into Postman (desktop, web, and CLI) and Insomnia (via its Postman v2 import path).

### What Gets Generated

- **Folders** grouped by the operation's first `tag`. Untagged operations appear at the collection root.
- **Requests** with method, URL, headers, body, and authentication block per endpoint.
- **OpenAPI-style path placeholders** (`/users/{userId}`) rewritten as Postman's `:userId` syntax, with captured example values promoted to per-request `variable[]` entries.
- **Query and header parameters** from the DSL with captured example values.
- **Request bodies** rendered as `mode: raw` with language (`json`, `xml`, `javascript`, `html`, or `text`) inferred from the captured `Content-Type`.
- **Response examples** — each test case becomes a saved response example under its endpoint, labelled with the `responseDescription` or `<status> response`.
- **Security schemes** translated to Postman's native `auth` block:
  - `HttpBearer` → Bearer Token
  - `HttpBasic` → Basic Auth
  - `ApiKeyInHeader` / `ApiKeyInQuery` / `ApiKeyInCookie` → API Key (with matching `in` location)
  - `OAuth2InBearer` / `OpenIdConnectInBearer` → OAuth 2.0 (token in header)
  - `OAuth2InCookie` / `OpenIdConnectInCookie` → API Key with `in: cookie` (Baklava doesn't capture the cookie name at scheme-definition time, so the user fills it in after import)
  - `MutualTls` → no `auth` block (no Postman equivalent; client-cert setup is external to the collection)
- **Collection-level variables** with empty placeholder values — `{{baseUrl}}` plus one per security scheme's credentials:
  - Bearer → `{scheme}Token`
  - Basic → `{scheme}Username` + `{scheme}Password`
  - API key (any `in`) → `{scheme}Value`
  - OAuth / OpenID Connect in bearer → `{scheme}Token`
  - OAuth / OpenID Connect in cookie → `{scheme}CookieName` + `{scheme}Token`

### Configuration

```scala
baklavaGenerateConfigs := Map(
  "postman.collectionName" -> "My API"
)
```

Defaults to `"Baklava-generated API"` when unset.

### Usage

After generating:

1. **Postman** — File menu → Import → pick `target/baklava/postman/collection.json`.
2. **Insomnia** — Application menu → Import → choose the file, select "Postman v2" when prompted.

After importing, set the `baseUrl` collection variable (e.g., `https://api.example.com`) plus any security-credential variables. Each request then sends against your live server with correct paths, headers, bodies, and auth.

### Caveats

- Postman permits only one `auth` block per request, so when an endpoint declares multiple `SecurityScheme`s, only the first maps to the native auth block. Users can switch alternatives manually in the Postman UI after import.
- Body serialization uses the raw captured string from the test. If your DSL passes a Scala case class whose JSON encoding has nested escaped strings, those will appear as-is in the request body (as they would on the wire).
- The generator does not emit Postman test scripts or pre-request scripts — it only reproduces the request/response shape. Response examples are attached for visual inspection, not for assertions.
