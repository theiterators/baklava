# Baklava-generated sttp-client

This directory contains a Scala source tree emitted from your Baklava test cases. It uses
[sttp-client4](https://sttp.softwaremill.com) and is framework-agnostic — generated functions
return `Request[Either[String, String]]` values that you `.send(backend)` with any sttp backend
(sync, async, fs2, Future, etc.).

## Layout

- `src/main/scala/baklavaclient/common/Types.scala` — case classes shared by 2+ tags (omitted if empty)
- `src/main/scala/baklavaclient/{tag}/Types.scala` — tag-local case classes (omitted if empty)
- `src/main/scala/baklavaclient/{tag}/Endpoints.scala` — one `{Tag}Endpoints` object with a
  `def` per endpoint

## Usage

Copy the files into your project under a matching package, add
`"com.softwaremill.sttp.client4" %% "core" % "4.x.y"` to your dependencies, then pick an endpoint
from one of the generated `*Endpoints.scala` files and supply any required auth, path, query,
header, or body parameters:

```scala
import sttp.client4._
import sttp.model.Uri
// import baklavaclient.<tag>.<Tag>Endpoints

val backend = DefaultSyncBackend()
val base    = uri"https://api.example.com"

// val req = <Tag>Endpoints.<operation>(baseUri = base /*, other params */)
// val res = req.send(backend)
```

Request bodies take a pre-serialized string (`bodyJson: String`) — bring your own codec (circe,
jsoniter, upickle, etc.) to produce it. The `Content-Type` on the generated request honors the
content type captured by Baklava; for JSON-only APIs that resolves to `application/json`.
