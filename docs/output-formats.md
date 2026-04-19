---
sidebar_position: 6
title: Output Formats
---

# Output Formats

Baklava supports six output formats. You can use one or more simultaneously — each is an independent SBT dependency that produces its own output in `target/baklava/`.

## How It Works

Formatters are **automatically discovered** via reflection. Any formatter on the test classpath is picked up and run — no registration or configuration needed (beyond format-specific config like `openapi-info`). Just add the dependency and it works:

```scala
libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-simple"     % "VERSION" % Test,  // adds Simple format
  "pl.iterators" %% "baklava-openapi"    % "VERSION" % Test,  // adds OpenAPI format
  "pl.iterators" %% "baklava-tsrest"     % "VERSION" % Test,  // adds TS-REST format
  "pl.iterators" %% "baklava-tsfetch"    % "VERSION" % Test,  // adds TypeScript fetch client
  "pl.iterators" %% "baklava-postman"    % "VERSION" % Test,  // adds Postman Collection format
  "pl.iterators" %% "baklava-sttpclient" % "VERSION" % Test   // adds Scala sttp-client stubs
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

## Scala sttp-client Format

**Dependency:** `"pl.iterators" %% "baklava-sttpclient" % "VERSION" % Test`
**Configuration:** Optional — `sttp-client-package` key in `baklavaGenerateConfigs`
**Output:** `target/baklava/sttpclient/`

Generates a tree of Scala source files containing [sttp-client4](https://sttp.softwaremill.com) request builders for every documented endpoint. Named JSON request and response bodies use [sttp-client4's circe integration](https://sttp.softwaremill.com/en/latest/json.html#circe) where applicable. The typed `Request[Either[ResponseException[String], T]]` shape is only emitted when the endpoint has a decodable typed 2xx JSON response; endpoints without a typed response — including ones that only have a typed request body — fall back to `Request[Either[String, String]]`. Either way, you send with any sttp backend (sync, async, Future, fs2, ZIO, etc.).

### Generated Files

- `README.md` — usage overview
- `src/main/scala/{package}/common/dtos.scala` — case classes shared by two or more tags (omitted if empty)
- `src/main/scala/{package}/{tag}/dtos.scala` — case classes used only within that tag (omitted if empty)
- `src/main/scala/{package}/{tag}/{Tag}Endpoints.scala` — one `{Tag}Endpoints` object with a `def` per endpoint. Untagged operations land in `default/DefaultEndpoints.scala`.

Package name defaults to `baklavaclient` and can be overridden via the `sttp-client-package` config key. Each `{Tag}Endpoints.scala` file emits `import` statements for the `common` sub-package and any cross-tag types it references, so method bodies can use short class names.

### Type Distribution

Each named schema is routed based on how many tags' endpoints reference it:

- Used by **one tag** → `{tag}/dtos.scala` in that tag's sub-package
- Used by **two or more tags** → `common/dtos.scala` under the `common` sub-package

`{Tag}Endpoints.scala` files emit Scala `import` statements pointing at the right sub-package, so endpoint method bodies can use the short class name directly.

### Typed bodies and responses

Request-body typing and response typing are decided independently.

**Typed request body** — when a request body resolves to a named case class (or `Seq[NamedClass]`) and all captures on the endpoint agree on a JSON-ish Content-Type:

- Signature becomes `body: SomeRequest`
- Serialization: `.body(body.asJson.noSpaces).contentType("<captured Content-Type>")` (reuses the captured value, e.g. `application/vnd.api+json; charset=utf-8`; falls back to `application/json` when none was captured)
- File imports `sttp.client4.circe._` + `io.circe.generic.auto._` + `io.circe.syntax._`

**Typed response** — when every 2xx capture has the same named case class (or `Seq[NamedClass]`) and declares a JSON-ish `responseContentType`:

- Return becomes `Request[Either[ResponseException[String], SomeResponse]]`
- Decoding: `.response(asJson[SomeResponse])`
- File imports `sttp.client4.circe._` + `io.circe.generic.auto._`

**Raw fallback** — when the body isn't a named case class (multipart/form, plain text, empty), the endpoint keeps the raw `bodyJson: String` input. When the response isn't a named JSON schema (or 2xx responses are mixed JSON and non-JSON), the endpoint keeps the raw `Either[String, String]` response. An endpoint can mix a typed body with a raw response (or vice versa) — each side is gated on its own.

Consumers need `sttp-client4-circe` and `circe-generic` on the classpath when any endpoint hits the typed paths above.

### Endpoint Shape

Each generated `def` is curried into two parameter lists. The first carries connection-level state that's typically set once per session; the second carries per-call inputs:

**First parameter list (connection-level):**

1. `baseUri: sttp.model.Uri`
2. Credential parameters per the first `SecurityScheme` (`{schemeName}Token` / `{schemeName}Username`+`{schemeName}Password` / `{schemeName}Value` / `{schemeName}CookieName`+`{schemeName}Token`). Scheme names that collide with Scala reserved words (e.g. `type`) are sanitized so the final identifier always compiles.

**Second parameter list (per-call):**

3. Path parameters as required positional parameters
4. Query parameters (required-typed or `Option[T] = None`)
5. Declared headers (same required/optional handling)
6. Either `body: SomeRequest` (typed path) or `bodyJson: String` (raw path, including multipart/form)

If an endpoint has no per-call inputs, the generator collapses the signature to a single param list so callers don't have to write trailing `()`.

The currying makes the connection vs. per-call separation visible at the call site and lets you partially apply the connection — `val getOnApi = getUser(base, token) _` gives you a function over per-call inputs you can pass around.

Example (typed, generated for `POST /users` returning `User`):

```scala
def createUser(
    baseUri: Uri,
    bearerAuthToken: String
)(
    body: CreateUserRequest
): Request[Either[ResponseException[String], User]] = {
  basicRequest
    .post(baseUri.addPath("users"))
    .header("Authorization", s"Bearer ${bearerAuthToken}")
    .body(body.asJson.noSpaces)
    .contentType("application/json")
    .response(asJson[User])
}
```

Example (raw fallback, generated for `POST /users/{userId}/photo` with `multipart/form-data`):

```scala
def uploadPhoto(
    baseUri: Uri,
    bearerAuthToken: String
)(
    userId: java.util.UUID,
    bodyJson: String
): Request[Either[String, String]] = {
  basicRequest
    .post(baseUri.addPath("users", s"$userId", "photo"))
    .header("Authorization", s"Bearer ${bearerAuthToken}")
    .body(bodyJson)
    .contentType("multipart/form-data; boundary=...")
}
```

### HTTP Methods

Well-known verbs (`GET`/`POST`/`PUT`/`DELETE`/`PATCH`/`HEAD`/`OPTIONS`) use the convenience builders on `basicRequest` (`.get(uri)`, `.post(uri)`, …). Uncommon or extension verbs (`PROPFIND`, `PURGE`, …) fall back to `.method(sttp.model.Method("X"), uri)`, so the generated code compiles regardless of what the DSL captured.

### Security Mappings

| Scheme                         | Credential parameter(s)                         | Wiring                                                             |
|--------------------------------|-------------------------------------------------|--------------------------------------------------------------------|
| `HttpBearer`                   | `{scheme}Token: String`                         | `.header("Authorization", s"Bearer ${...Token}")`                  |
| `HttpBasic`                    | `{scheme}Username`, `{scheme}Password: String`  | `.auth.basic(username, password)`                                  |
| `ApiKeyInHeader`               | `{scheme}Value: String`                         | `.header("<key>", value)`                                          |
| `ApiKeyInCookie`               | `{scheme}Value: String`                         | `.cookie("<key>", value)`                                          |
| `ApiKeyInQuery`                | `{scheme}Value: String`                         | `.addParam("<key>", value)` on the URI chain                       |
| `OAuth2InBearer`/`OpenIdConnectInBearer` | `{scheme}Token: String`               | `.header("Authorization", s"Bearer ${...Token}")`                  |
| `OAuth2InCookie`/`OpenIdConnectInCookie` | `{scheme}CookieName`, `{scheme}Token: String` | `.cookie({scheme}CookieName, {scheme}Token)` (cookie name isn't part of the scheme) |
| `MutualTls`                    | —                                               | Not wired — client-cert setup is external to the generated code    |

Only the first `SecurityScheme` maps to generated parameters. Endpoints using multiple schemes need additional headers supplied manually.

### Content-Type

Endpoints with a request body emit `.contentType(...)` honoring the content-type captured by Baklava. When every call on an endpoint declared the same content-type (e.g. `multipart/form-data`), the generated code uses that value; otherwise it defaults to `application/json`. The `bodyJson` parameter name is a historical convention — the generator itself doesn't assume JSON, so you can pass any pre-serialized payload.

### Schema → Scala Type Mapping

| Baklava Schema | Scala |
|---|---|
| `String` | `String` |
| `String` (uuid) | `java.util.UUID` |
| `String` (enum) | `String` (user refines manually if desired) |
| `Int` | `Int` |
| `Long` (int64 format) | `Long` |
| `Float`, `Double`, `BigDecimal` | `Float`, `Double`, `BigDecimal` |
| `Boolean` | `Boolean` |
| `Seq/List/Vector/Set/Array[T]` | `Seq[T]` |
| Named case class | Case class (emitted in the owning tag's `dtos.scala` or `common/dtos.scala`) |
| `Option[T]` | Field becomes `Option[T] = None` |

### Configuration

```scala
baklavaGenerateConfigs := Map(
  "sttp-client-package" -> "com.example.api.client"
)
```

### Usage in a Scala Project

Copy the generated tree into your project under a matching package, add sttp-client4 + the circe integration + circe-generic (for codec auto-derivation) to your dependencies:

```scala
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client4" %% "core"          % "4.x.y",
  "com.softwaremill.sttp.client4" %% "circe"         % "4.x.y",
  "io.circe"                      %% "circe-generic" % "0.14.x"
)
```

Then pick an endpoint from one of the generated `*Endpoints.scala` files and supply its required auth, path, query, header, or body parameters:

```scala
import sttp.client4._
import sttp.model.Uri
import com.example.api.client.users.UsersEndpoints
import com.example.api.client.common.User

val backend = DefaultSyncBackend()
val base    = uri"https://api.example.com"

val req = UsersEndpoints.listUsers(base, bearerAuthToken = "jwt...")(page = Some(1))
val res = req.send(backend) // Either[ResponseException[String], PaginatedUsers]
res.body match {
  case Right(page)                                                 => println(page.users)
  case Left(ResponseException.DeserializationException(raw, err))  => println(s"bad JSON: $err")
  case Left(ResponseException.UnexpectedStatusCode(raw))           => println(s"HTTP error: $raw")
}
```

The generated code imports `sttp.client4._` (compatible with Scala 2.13 and 3.3+).

### Caveats

- Only the first `SecurityScheme`'s credentials become function parameters. Endpoints using multiple schemes need additional headers supplied manually.
- Typed bodies/responses require `sttp-client4-circe` + a circe `Encoder`/`Decoder` in scope. The generator emits `import io.circe.generic.auto._` to auto-derive; replace with semi-auto or hand-written codecs if you need override control.
- The raw fallback path (non-JSON captured Content-Type, unnamed body schema) takes `bodyJson: String` — the generator has no opinion on which codec library you use there.
- Enum values are emitted as plain `String`. If you want a sealed trait, refine `dtos.scala` manually after generation.

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
| Case class with properties | Named `interface` (re-exported as `T.ClassName`) |
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

- Only the first `SecurityScheme`'s matching credential is wired into `authHeaders()` automatically. Endpoints using API-key-in-query or API-key-in-cookie schemes need to be set manually (via `client.apiKeys` or by passing an extra header parameter).
- The return type is derived from the first 2xx response's schema. When a single endpoint has multiple 2xx variants with different schemas, only the first wins — matching the OpenAPI generator's existing first-schema-wins policy.
- Non-object response bodies are returned as-is (e.g. plain strings or numbers). The generator calls `JSON.parse` unconditionally, which is safe for JSON-encoded primitives but will fail on raw text response bodies — users serving plain text should parse the response themselves via a custom `fetch` wrapper.
