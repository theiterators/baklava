---
sidebar_position: 5
title: Remote APIs (sttp)
---

# Documenting Remote APIs with sttp

`baklava-sttp` documents APIs you *consume* rather than routes you serve. It sends each
test request over the network with [sttp-client4](https://sttp.softwaremill.com/) — no
HTTP server stack, no effect runtime, no `performRequest` overrides.

## Installation

```scala
libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-sttp" % "VERSION" % Test,
  "pl.iterators" %% "baklava-scalatest" % "VERSION" % Test, // or -specs2 / -munit
  "pl.iterators" %% "baklava-openapi" % "VERSION" % Test    // or any other output format
)
```

## Usage

The only thing you must provide is `baseUri`:

```scala
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.scalatest.{BaklavaScalatest, ScalatestAsExecution}
import pl.iterators.baklava.sttp4.{BaklavaSttp, FromSttpBody, ToSttpBody}
import sttp.client4.SyncBackend
import sttp.model.{Header, Method, StatusCode, Uri}

class GitHubApiSpec
    extends AnyFunSpec
    with Matchers
    with BaklavaSttp[Unit, Unit, ScalatestAsExecution]
    with BaklavaScalatest[SyncBackend, ToSttpBody, FromSttpBody] {

  override def baseUri: Uri = Uri.unsafeParse("https://api.github.com")
  override def defaultHeaders: Seq[Header] = Seq(Header("User-Agent", "baklava-demo"))

  path("/orgs/{org}")(
    supports(
      Method.GET,
      pathParameters = p[String]("org"),
      summary = "Get an organization",
      operationId = "getOrg",
      tags = Seq("orgs")
    )(
      onRequest(pathParameters = "theiterators")
        .respondsWith[String](StatusCode.Ok, description = "organization profile")
        .assert { ctx =>
          ctx.performRequest(defaultBackend)
        }
    )
  )
}
```

JSON request and response bodies work with plain circe codecs — any type with an
`io.circe.Encoder` can be a request body, any type with an `io.circe.Decoder` can be
used in `respondsWith[T]`.

## Adapter defaults

- `baseUri` may include a path prefix (e.g. `https://api.example.com/api/v3`) but must not
  carry a query string or fragment — `resolveUri` rejects those with an `IllegalArgumentException`.
- `strictHeaderCheckDefault` is `false` — remote services always send headers your test
  does not declare. Opt back in per request with `strictHeaderCheck = true`.
- `defaultBackend` is sttp's `DefaultSyncBackend()`; override the `lazy val` to configure
  proxies, TLS, timeouts, or to wrap the backend (e.g. logging).
- `defaultHeaders` are added to every request; headers declared in a test win on
  name conflict. Good for `User-Agent` or auth tokens from the environment.
- Response bodies are read fully into memory, so assertions and the documentation
  serializer can both consume them.

## Notes

- The DSL (`path`/`supports`/`onRequest`), schema derivation, and every output format
  work exactly as with the server adapters — see the [DSL Reference](dsl-reference.md).
- For authenticated APIs prefer documenting security with the `security` DSL; secrets
  can go into `defaultHeaders` from environment variables.
- Mind the target API's rate limits: every `onRequest` case performs a real HTTP call.
