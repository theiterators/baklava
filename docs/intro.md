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
2. Run your tests with `sbt test` — Baklava serializes each test case to JSON in `target/baklava/calls/<suite>/`
3. The SBT plugin merges the captured calls into a persistent store (`target/baklava/store/`) and generates documentation from it

:::tip[Incremental runs are fine]

Captured calls are kept in a persistent store under `target/baklava/store/`, one directory per test suite. Every run replaces the entries of the suites that ran and keeps the rest, then regenerates the documentation from the whole store. Plain `sbt test` therefore produces complete documentation even on sbt 2, whose `test` is incremental and may run only some suites, or none. The one exception is right after `clean` (or `baklavaClean`): the store is gone, and on a warm sbt 2 cache `test` may run nothing, in which case Baklava skips generation and prints a message. Run `sbt testFull` once to rebuild the store.

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
