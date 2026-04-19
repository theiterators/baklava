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
  "pl.iterators" %% "baklava-simple"     % "VERSION" % Test,  // adds Simple format
  "pl.iterators" %% "baklava-openapi"    % "VERSION" % Test,  // adds OpenAPI format
  "pl.iterators" %% "baklava-tsrest"     % "VERSION" % Test,  // adds TS-REST format
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

## Scala sttp-client Format

**Dependency:** `"pl.iterators" %% "baklava-sttpclient" % "VERSION" % Test`
**Configuration:** Optional — `sttp-client-package` key in `baklavaGenerateConfigs`
**Output:** `target/baklava/sttpclient/`

Generates a tree of Scala source files containing [sttp-client4](https://sttp.softwaremill.com) request builders for every documented endpoint. The generated code is framework-agnostic — each endpoint is a `def` that returns a `Request[Either[String, String]]` value. You send it with whatever sttp backend you like (sync, async, Future, fs2, ZIO, etc.) and bring your own JSON codec library.

### Generated Files

- `README.md` — usage overview
- `src/main/scala/{package}/Types.scala` — case classes for every named object schema captured in the API
- `src/main/scala/{package}/{Tag}Endpoints.scala` — one object per operation tag, with one `def` per endpoint. Untagged operations land in `DefaultEndpoints.scala`.

Package name defaults to `baklavaclient` and can be overridden via the `sttp-client-package` config key.

### Endpoint Shape

Each generated `def` takes:
- Path parameters as required positional parameters
- Query parameters (required-typed or `Option[T] = None`)
- Declared headers (same required/optional handling)
- A `bodyJson: String` parameter when the operation has a request body — users supply pre-serialized JSON from their own codec library
- Credential parameters per the first `SecurityScheme` (`{schemeName}Token` / `{schemeName}Username`+`{schemeName}Password` / `{schemeName}Value`)
- A trailing `baseUri: sttp.model.Uri` parameter

Example (generated for `GET /users/{userId}` with `bearerAuth`):

```scala
def getUser(
    userId: String,
    bearerAuthToken: String,
    baseUri: Uri
): Request[Either[String, String]] = {
  basicRequest
    .get(baseUri.addPath("users", s"$userId"))
    .header("Authorization", s"Bearer ${bearerAuthToken}")
}
```

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
| Named case class | Case class (emitted in `Types.scala`) |
| `Option[T]` | Field becomes `Option[T] = None` |

### Configuration

```scala
baklavaGenerateConfigs := Map(
  "sttp-client-package" -> "com.example.api.client"
)
```

### Usage in a Scala Project

Copy the generated tree into your project under a matching package, add `"com.softwaremill.sttp.client4" %% "core" % "4.x.y"` to your dependencies, then:

```scala
import sttp.client4.*
import sttp.model.Uri
import com.example.api.client.*

val backend = DefaultSyncBackend()
val base    = uri"https://api.example.com"

val req = UsersEndpoints.listUsers(bearerAuthToken = "jwt...", baseUri = base)
val res = req.send(backend)
```

### Caveats

- Only the first `SecurityScheme`'s credentials become function parameters. Endpoints using multiple schemes need additional headers supplied manually.
- Request bodies are always passed as `String` — the generator has no opinion on which JSON library you use. This keeps the module dependency-free but means you handle serialization at the call site.
- Responses come back as `Either[String, String]`; deserialize yourself with the codec of your choice.
- Enum values are emitted as plain `String`. If you want a sealed trait, refine the `Types.scala` manually after generation.
