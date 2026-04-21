# Baklava-generated sttp-client

This directory contains a Scala source tree emitted from your Baklava test cases. It uses
[sttp-client4](https://sttp.softwaremill.com) and is framework-agnostic — generated functions
return `sttp.client4.Request[R]` values that you `.send(backend)` with any sttp backend
(sync, async, fs2, Future, etc.).

## Layout

- `src/main/scala/baklavaclient/common/dtos.scala` — case classes shared by 2+ tags (omitted if empty)
- `src/main/scala/baklavaclient/{tag}/dtos.scala` — tag-local case classes (omitted if empty)
- `src/main/scala/baklavaclient/{tag}/{Tag}Endpoints.scala` — one `{Tag}Endpoints` object with a
  `def` per endpoint

## Typed bodies and responses (circe)

Whenever a request body or 2xx response maps to a named case class (or `Seq`/`List` of one),
the generated `def` takes that type directly (`body: MyRequest`) and returns
`Request[Either[ResponseException[String], MyResponse]]`. The file imports
`sttp.client4.circe._`, encodes typed request bodies explicitly via `body.asJson.noSpaces`,
and decodes typed responses with `asJson[T]` — you need circe `Encoder`/`Decoder` instances
in scope (e.g. via `io.circe.generic.auto._`).

Endpoints whose body/response isn't a named schema (multipart, plain-text, empty) keep the
raw `bodyJson: String` input and the `Either[String, String]` response, so you can still
use them without circe.

## Dependencies

```scala
libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client4" %% "core"  % "4.x.y",
  "com.softwaremill.sttp.client4" %% "circe" % "4.x.y",
  "io.circe"                      %% "circe-core"    % "0.14.x",
  "io.circe"                      %% "circe-generic" % "0.14.x"  // for `generic.auto._`
)
```

## Usage

```scala
import sttp.client4._
import sttp.client4.circe._
import sttp.model.Uri
import io.circe.generic.auto._
// import baklavaclient.<tag>.<Tag>Endpoints
// import baklavaclient.<tag>.dtos._

val backend = DefaultSyncBackend()
val base    = uri"https://api.example.com"

// val req = <Tag>Endpoints.<operation>(baseUri = base /*, typed body + params */)
// val res = req.send(backend)  // Either[ResponseException[String], T]
```
