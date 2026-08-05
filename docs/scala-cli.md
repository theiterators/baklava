---
sidebar_position: 10
title: Standalone Scripts (scala-cli)
---

# Standalone Scripts with scala-cli

You don't need an sbt project to use Baklava. With [scala-cli](https://scala-cli.virtuslab.org/) a single file can run tests and generate documentation — handy for:

- **Trying Baklava in two minutes**, with zero build setup.
- **Documenting a third-party API**: point Baklava at any HTTP service, assert on its real responses, and get an OpenAPI spec plus typed clients from behavior you have verified.

The script below tests two endpoints of the **live GitHub REST API** through the [`baklava-sttp` remote-API adapter](sttp.md) and produces an OpenAPI spec and an [sttp-client4](https://sttp.softwaremill.com) source tree.

## The script

Save as `github-api-docs.test.scala`:

```scala
//> using scala 3.3.8
//> using dep pl.iterators::baklava-sttp:2.1.0
//> using dep pl.iterators::baklava-munit:2.1.0
//> using dep pl.iterators::baklava-openapi:2.1.0
//> using dep pl.iterators::baklava-sttpclient:2.1.0
//> using dep io.circe::circe-generic:0.14.16

import io.circe.generic.auto.*
import pl.iterators.baklava.BaklavaGenerate
import pl.iterators.baklava.munit.{BaklavaMunit, MunitAsExecution}
import pl.iterators.baklava.sttp4.{BaklavaSttp, FromSttpBody, ToSttpBody}
import sttp.client4.SyncBackend
import sttp.model.Method.*
import sttp.model.StatusCode.*
import sttp.model.{Header, Uri}

case class GitHubUser(login: String, id: Long, name: Option[String], company: Option[String], public_repos: Int, followers: Int)
case class GitHubRepo(name: String, full_name: String, description: Option[String], stargazers_count: Int, language: Option[String])
case class GitHubError(message: String, documentation_url: Option[String])

class GitHubApiSpec
    extends BaklavaMunit[SyncBackend, ToSttpBody, FromSttpBody]
    with BaklavaSttp[Unit, Unit, MunitAsExecution] {

  override def baseUri: Uri                = Uri.unsafeParse("https://api.github.com")
  override def defaultHeaders: Seq[Header] = Seq(Header("User-Agent", "baklava-demo"))

  path("/users/{username}")(
    supports(
      GET,
      pathParameters = p[String]("username"),
      summary = "Get a user",
      operationId = "getUser",
      tags = Seq("users")
    )(
      onRequest(pathParameters = "octocat")
        .respondsWith[GitHubUser](Ok, description = "User found")
        .assert { ctx =>
          val response = ctx.performRequest(defaultBackend)
          assertEquals(response.body.login, "octocat")
        },
      onRequest(pathParameters = "no-such-user-baklava-4711")
        .respondsWith[GitHubError](NotFound, description = "User not found")
        .assert { ctx =>
          ctx.performRequest(defaultBackend)
        }
    )
  )

  path("/repos/{owner}/{repo}")(
    supports(
      GET,
      pathParameters = (p[String]("owner"), p[String]("repo")),
      summary = "Get a repository",
      operationId = "getRepository",
      tags = Seq("repos")
    )(
      onRequest(pathParameters = ("theiterators", "baklava"))
        .respondsWith[GitHubRepo](Ok, description = "Repository found")
        .assert { ctx =>
          val response = ctx.performRequest(defaultBackend)
          assertEquals(response.body.full_name, "theiterators/baklava")
        }
    )
  )

  // what the sbt plugin normally does after `sbt test`
  override def afterAll(): Unit = {
    super.afterAll()
    def cfg(key: String, value: String) =
      s"$key|${java.util.Base64.getEncoder.encodeToString(value.getBytes("UTF-8"))}"
    BaklavaGenerate.main(
      Array(
        cfg(
          "openapi-info",
          """openapi: 3.0.1
            |info:
            |  title: GitHub REST API (excerpt)
            |  version: 1.0.0
            |""".stripMargin
        ),
        cfg("sttp-client-package", "demo.github")
      )
    )
  }
}
```

## Run it

```bash
scala-cli test github-api-docs.test.scala
```

```
Test run GitHubApiSpec started
GitHubApiSpec: finished 1.77s
Test run GitHubApiSpec finished: 0 failed, 0 ignored, 3 total
```

The three tests hit `api.github.com` for real — the assertions run against live responses, so the generated documentation reflects verified behavior, including the captured response bodies as examples.

## What you get

`target/baklava/openapi/openapi.yml` (trimmed — real output embeds full response examples):

```yaml
openapi: 3.0.1
info:
  title: GitHub REST API (excerpt)
  version: 1.0.0
paths:
  /users/{username}:
    get:
      tags: [users]
      summary: Get a user
      operationId: getUser
      parameters:
      - name: username
        in: path
        required: true
        schema:
          type: string
        examples:
          User found:
            value: octocat
          User not found:
            value: no-such-user-baklava-4711
      responses:
        "200":
          description: User found
          content:
            application/json:
              schema:
                required: [followers, id, login, public_repos]
                type: object
                properties:
                  login: { type: string }
                  id: { type: integer, format: int64 }
                  name: { type: string }
                  # ...
        "404":
          description: User not found
          # ...
  /repos/{owner}/{repo}:
    # ...
```

`target/baklava/sttpclient/` — a ready-to-use sttp-client4 source tree:

```scala
package demo.github.users

object UsersEndpoints {

  /** Get a user */
  def getUser(
      baseUri: Uri
  )(
      username: String
  ): Request[Either[ResponseException[String], GitHubUser]] = {
    basicRequest
      .get(baseUri.addPath("users", s"$username"))
      .response(asJson[GitHubUser])
  }
}
```

```scala
package demo.github.users

final case class GitHubUser(company: Option[String] = None, followers: Int, id: Long, login: String, name: Option[String] = None, public_repos: Int)
```

## How it works

- **Remote APIs via `baklava-sttp`**: the [sttp adapter](sttp.md) sends every request over the network with sttp-client4, resolved against `baseUri` — no HTTP server stack, no effect runtime, no `performRequest` override. Strict header checking is off by default (remote services always send undeclared headers), and response bodies are read fully into memory so both your assertions and the serializer can consume them. JSON codecs come from plain circe `Encoder`/`Decoder` instances — `circe-generic`'s auto derivation is enough here.
- **Generation in `afterAll`**: the sbt plugin normally runs `BaklavaGenerate` after `sbt test`. In a standalone script we call it from `afterAll` instead, so a single `scala-cli test` invocation runs the tests and generates output. Config entries use the `key|<base64 value>` argument format; formatters are auto-discovered from the classpath, so which outputs you get is controlled purely by the `//> using dep` lines — swap in `baklava-tsrest`, `baklava-orpc`, `baklava-simple`, or `baklava-postman` to taste.
- **Rate limits**: unauthenticated GitHub API calls are limited to 60/hour per IP; this script makes 3 per run. If you document an API that needs auth, add the header to `defaultHeaders` (e.g. a bearer token from an environment variable) or document it properly with Baklava's `security` DSL — see [DSL Reference](dsl-reference.md).
