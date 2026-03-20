---
sidebar_position: 1
title: Introduction to Baklava
---
## Baklava
##### Generate OpenAPI, HTML Docs or TypeScript client interface from routing tests.

Baklava is a Scala library that generates API documentation directly from your HTTP routing tests. Instead of maintaining documentation separately, your tests become the single source of truth for your API specification.

### Supported stacks

- **HTTP Servers**: Pekko HTTP, http4s
- **Test Frameworks**: ScalaTest, Specs2, MUnit
- **Output Formats**: OpenAPI (with optional SwaggerUI), Simple, TS-REST
- **Scala Versions**: 2.13 and 3 (LTS)
- **JDK**: 11+

### How it works

1. Write routing tests using the Baklava DSL — define paths, methods, parameters, request/response examples
2. Run your tests with `sbt test` — Baklava serializes each test case to JSON in `target/baklava/calls/`
3. The SBT plugin automatically generates documentation from the collected test cases

### Quick links

- [Installation](installation.md)
- [Pekko HTTP Integration](pekko-http.md)
- [http4s Integration](http4s.md)
- [DSL Reference](dsl-reference.md)
- [Examples](examples.md)
- [Configuration](configuration.md)

A library maintained by [Iterators](https://www.iteratorshq.com).
