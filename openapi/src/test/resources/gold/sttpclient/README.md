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

Request-body and response typing are decided independently:

 - When a request body maps to a named case class (or `Seq`/`List` of one), the `def`
   takes `body: MyRequest` directly and serializes it via `body.asJson.noSpaces`.
 - When a 2xx response maps to a named case class and all successful captures
   declare a JSON-ish Content-Type, the `def` returns
   `Request[Either[ResponseException[String], MyResponse]]` and uses `asJson[T]`.

Either signal (typed body, typed response, or both) adds
`import sttp.client4.circe._` + `import io.circe.generic.auto._`; a typed body additionally
adds `import io.circe.syntax._`. You need circe `Encoder`/`Decoder` instances in scope
(e.g. via `io.circe.generic.auto._`).

Endpoints whose body isn't a named schema (multipart, plain-text, empty) keep the
raw `bodyJson: String` input. Endpoints whose 2xx response isn't a named JSON schema keep
the raw `Either[String, String]` response, so you can still use them without circe.

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
