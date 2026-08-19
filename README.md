<p align="center">
  <img src="logo.png" alt="Baklava" width="180" />
</p>

# Baklava

[![Maven Central](https://img.shields.io/maven-central/v/pl.iterators/baklava-core_2.13)](https://central.sonatype.com/namespace/pl.iterators)
[![CI](https://github.com/theiterators/baklava/actions/workflows/ci.yml/badge.svg)](https://github.com/theiterators/baklava/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

**Generate OpenAPI, HTML docs, or TypeScript client contracts from your routing tests.**

Baklava is a Scala library that turns your HTTP tests — for routes you serve or third-party APIs you consume — into API documentation. Instead of maintaining docs separately, your tests become the single source of truth.

## Supported Stacks

| | Options |
|---|---|
| HTTP Integration | Pekko HTTP, http4s (routes you serve), sttp (remote APIs you consume) |
| Test Frameworks | ScalaTest, Specs2, MUnit |
| Output Formats | OpenAPI (+ SwaggerUI), Simple HTML, TS-REST, oRPC, ts-fetch, sttp-client, Postman |
| Scala | 2.13, 3 (LTS) |
| JDK | 11+ |

## Quick Start

**1. Add the SBT plugin** to `project/plugins.sbt`:

```scala
addSbtPlugin("pl.iterators" % "baklava-sbt-plugin" % "2.1.0")
```

**2. Enable the plugin** in `build.sbt`:

```scala
enablePlugins(BaklavaSbtPlugin)
```

**3. Add dependencies** (pick one from each group):

```scala
libraryDependencies ++= Seq(
  // HTTP integration — choose one
  "pl.iterators" %% "baklava-pekko-http" % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-http4s" % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-sttp"   % "2.1.0" % Test, // for remote APIs you consume

  // Test framework — choose one
  "pl.iterators" %% "baklava-scalatest"  % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-specs2"  % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-munit"   % "2.1.0" % Test,

  // Output format — one or more
  "pl.iterators" %% "baklava-openapi"    % "2.1.0" % Test,
  "pl.iterators" %% "baklava-simple"     % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-tsrest"    % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-orpc"      % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-tsfetch"   % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-postman"   % "2.1.0" % Test,
  // "pl.iterators" %% "baklava-sttpclient" % "2.1.0" % Test,
)
```

**4. Configure** in `build.sbt`:

```scala
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false,
    baklavaGenerateConfigs := Map(
      "openapi-info" ->
        s"""
          |openapi: 3.0.1
          |info:
          |  title: My API
          |  version: 1.0.0
          |""".stripMargin
    )
  )
)
```

**5. Write a test:**

```scala
class UserSpec extends AnyFunSpec
    with BaklavaPekkoHttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller] {

  // ... setup ...

  path("/users/{userId}")(
    supports(
      GET,
      pathParameters = p[Long]("userId"),
      summary = "Get user by ID",
      tags = Seq("Users")
    )(
      onRequest(pathParameters = 1L)
        .respondsWith[User](OK, description = "User found")
        .assert { ctx =>
          val response = ctx.performRequest(routes)
          response.body.name shouldBe "Alice"
        },
      onRequest(pathParameters = 999L)
        .respondsWith[ErrorResponse](NotFound, description = "User not found")
        .assert { ctx =>
          ctx.performRequest(routes)
        }
    )
  )
}
```

**6. Run tests** and documentation is generated automatically:

```bash
sbt test
# Output in target/baklava/openapi/openapi.yml, target/baklava/simple/, etc.
```

> [!WARNING]
> **On sbt 2, use `sbt testFull` to generate documentation.** sbt 2 redefined `test` as an incremental task cached in a global store (`~/.cache/sbt`) that survives `clean` — on a warm cache it may run only a subset of your suite, or nothing at all, and the generated documentation only covers the tests that actually ran. `testFull` is the uncached, run-everything task. (Baklava refuses to overwrite existing output when zero calls were captured, but a partial run still produces partial documentation.)

## Try It Without a Build

Prefer to see it working first? A single [scala-cli](https://scala-cli.virtuslab.org/) script can test the **live GitHub REST API** and generate an OpenAPI spec plus a typed sttp client from verified responses — no sbt project needed:

```bash
scala-cli test github-api-docs.test.scala
```

Grab the script from [Standalone Scripts with scala-cli](https://theiterators.github.io/baklava/docs/scala-cli).

## Output Formats

**OpenAPI** generates a standard `openapi.yml` spec. Optionally serve it via SwaggerUI with `baklava-pekko-http-routes`.

**Simple HTML** generates self-contained, browsable HTML pages with no external dependencies.

**TS-REST** generates a TypeScript npm package with [ts-rest](https://ts-rest.com/) contracts and [Zod](https://zod.dev/) schemas for type-safe frontend API clients.

**oRPC** generates a TypeScript npm package with [oRPC](https://orpc.dev) contracts (`@orpc/contract` + Zod), consumable from any frontend via `OpenAPILink` with first-class TanStack Query support.

All formatters are auto-discovered from the classpath. Just add the dependency and it works.

## Documentation

Full documentation is available at [theiterators.github.io/baklava](https://theiterators.github.io/baklava/).

- [Installation](https://theiterators.github.io/baklava/docs/installation)
- [Pekko HTTP Integration](https://theiterators.github.io/baklava/docs/pekko-http)
- [http4s Integration](https://theiterators.github.io/baklava/docs/http4s)
- [DSL Reference](https://theiterators.github.io/baklava/docs/dsl-reference)
- [Examples](https://theiterators.github.io/baklava/docs/examples)
- [Standalone Scripts (scala-cli)](https://theiterators.github.io/baklava/docs/scala-cli)
- [Output Formats](https://theiterators.github.io/baklava/docs/output-formats)
- [Configuration](https://theiterators.github.io/baklava/docs/configuration)

## License

Apache 2.0 - see [LICENSE](LICENSE) for details.

Maintained by [Iterators](https://www.iteratorshq.com).
