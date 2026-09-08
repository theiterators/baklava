---
sidebar_position: 1
title: Introduction to Baklava
---
## Baklava
##### Turn your HTTP tests into OpenAPI, HTML docs, Postman collections, and typed TypeScript or Scala clients, for APIs you serve or consume.

Baklava is a Scala library that generates API documentation and client code directly from your HTTP tests — for routes you serve or third-party APIs you consume. Instead of maintaining documentation separately, your tests become the single source of truth for your API specification.

### Supported stacks

- **HTTP Integration**: Pekko HTTP, http4s (for routes you serve), or sttp (for remote APIs you consume)
- **Test Frameworks**: ScalaTest, Specs2, MUnit
- **Output Formats**: OpenAPI (with optional SwaggerUI), Simple HTML, TS-REST, oRPC, ts-fetch, sttp-client, Postman
- **Scala Versions**: 2.13 and 3 (LTS)
- **JDK**: 11+

### How it works

1. Write routing tests using the Baklava DSL — define paths, methods, parameters, request/response examples
2. Run your tests with `sbt test` — Baklava serializes each test case to JSON in `target/baklava/calls/`
3. The SBT plugin automatically generates documentation from the collected test cases

:::warning[sbt 2: use `testFull` to generate documentation]

sbt 2 redefined `test` as an incremental task cached in a global store (`~/.cache/sbt`) that survives `clean` — on a warm cache it may run only a subset of your suite, or nothing at all, and the generated documentation only covers the tests that actually ran. Run `sbt testFull` (the uncached, run-everything task) whenever you want complete documentation. Baklava refuses to overwrite existing output when zero calls were captured, but a partial run still produces partial documentation.

:::

### Quick links

- [Installation](installation.md)
- [Pekko HTTP Integration](pekko-http.md)
- [http4s Integration](http4s.md)
- [Remote APIs (sttp)](sttp.md)
- [DSL Reference](dsl-reference.md)
- [Examples](examples.md)
- [Configuration](configuration.md)

A library maintained by [Iterators](https://www.iteratorshq.com).
