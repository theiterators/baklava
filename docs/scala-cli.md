---
sidebar_position: 10
title: Standalone Scripts (scala-cli)
---

# Standalone Scripts with scala-cli

You don't need an sbt project to use Baklava. With [scala-cli](https://scala-cli.virtuslab.org/) a single file can run tests and generate documentation — handy for:

- **Trying Baklava in two minutes**, with zero build setup.
- **Documenting a third-party API**: point Baklava at any HTTP service, assert on its real responses, and get an OpenAPI spec plus typed clients from behavior you have verified.

The script below tests two endpoints of the **live GitHub REST API** and produces an OpenAPI spec and an [sttp-client4](https://sttp.softwaremill.com) source tree.

## The script

Save as `github-api-docs.test.scala`:

```scala
//> using scala 3.3.8
//> using dep pl.iterators::baklava-http4s:2.0.0
//> using dep pl.iterators::baklava-munit:2.0.0
//> using dep pl.iterators::baklava-openapi:2.0.0
//> using dep pl.iterators::baklava-sttpclient:2.0.0
//> using dep org.http4s::http4s-ember-client:0.23.36
//> using dep org.http4s::http4s-circe:0.23.36
//> using dep io.circe::circe-generic:0.14.16

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import io.circe.generic.auto.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Header, HttpRoutes, Request, Response, Uri}
import org.http4s.Method.*
import org.http4s.Status.*
import org.typelevel.ci.CIString
import pl.iterators.baklava.BaklavaGenerate
import pl.iterators.baklava.http4s.BaklavaHttp4s
import pl.iterators.baklava.munit.{BaklavaMunit, MunitAsExecution}

case class GitHubUser(login: String, id: Long, name: Option[String], company: Option[String], public_repos: Int, followers: Int)
case class GitHubRepo(name: String, full_name: String, description: Option[String], stargazers_count: Int, language: Option[String])
case class GitHubError(message: String, documentation_url: Option[String])

class GitHubApiSpec
    extends BaklavaMunit[HttpRoutes[IO], BaklavaHttp4s.ToEntityMarshaller, BaklavaHttp4s.FromEntityUnmarshaller]
    with BaklavaHttp4s[Unit, Unit, MunitAsExecution] {

  implicit val runtime: IORuntime                = IORuntime.global
  override def strictHeaderCheckDefault: Boolean = false

  // baklava normally tests local routes; for a remote API we send each request with a real client
  val routes: HttpRoutes[IO] = HttpRoutes.empty[IO]

  override def performRequest(routes: HttpRoutes[IO], request: Request[IO]): Response[IO] =
    EmberClientBuilder
      .default[IO]
      .build
      .use { client =>
        val remote = request
          .withUri(Uri.unsafeFromString(s"https://api.github.com${request.uri}"))
          .putHeaders(Header.Raw(CIString("User-Agent"), "baklava-demo"))
        client.run(remote).use { response =>
          response.body.compile.toList.map(bytes => response.copy(body = fs2.Stream.emits(bytes)))
        }
      }
      .unsafeRunSync()

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
          val response = ctx.performRequest(routes)
          assertEquals(response.body.login, "octocat")
        },
      onRequest(pathParameters = "no-such-user-baklava-4711")
        .respondsWith[GitHubError](NotFound, description = "User not found")
        .assert { ctx =>
          ctx.performRequest(routes)
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
          val response = ctx.performRequest(routes)
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
GitHubApiSpec: finished 7.61s
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

- **Remote APIs via `performRequest`**: Baklava's DSL builds an `http4s` `Request[IO]` and hands it to `performRequest`, which normally runs it against local routes. Overriding it to send the request with a real client (Ember here) turns Baklava into a documentation-generating integration test for any HTTP service. The response body is compiled to memory so it can be read both by your assertions and by the serializer.
- **Generation in `afterAll`**: the sbt plugin normally runs `BaklavaGenerate` after `sbt test`. In a standalone script we call it from `afterAll` instead, so a single `scala-cli test` invocation runs the tests and generates output. Config entries use the `key|<base64 value>` argument format; formatters are auto-discovered from the classpath, so which outputs you get is controlled purely by the `//> using dep` lines — swap in `baklava-tsrest`, `baklava-orpc`, `baklava-simple`, or `baklava-postman` to taste.
- **Rate limits**: unauthenticated GitHub API calls are limited to 60/hour per IP; this script makes 3 per run. If you document an API that needs auth, add the header in `performRequest` (e.g. a bearer token from an environment variable) or document it properly with Baklava's `security` DSL — see [DSL Reference](dsl-reference.md).
