---
sidebar_position: 2
title: Installation
---

# Installation

To use Baklava, you need to choose:

1. **HTTP Server**: Pekko HTTP or HTTP4s
2. **Test Framework**: Specs2, ScalaTest, or MUnit
3. **Output Format**: Simple, OpenAPI, or TS-REST

## Quick Setup Guide

### 1. Add the SBT Plugin

Add the Baklava plugin to your `project/plugins.sbt`:
```scala
addSbtPlugin("pl.iterators" % "baklava-sbt-plugin" % "VERSION")
```

Enable the plugin in your `build.sbt`:
```scala
enablePlugins(BaklavaSbtPlugin)
```

### 2. Choose Your Dependencies

Add the required dependencies to your `build.sbt` based on your choices:

#### Core Dependencies (Required)

**HTTP Server Integration** - Choose one:
```scala
// For Pekko HTTP
libraryDependencies += "pl.iterators" %% "baklava-pekko-http" % "VERSION" % Test

// For HTTP4s
libraryDependencies += "pl.iterators" %% "baklava-http4s" % "VERSION" % Test
```

**Test Framework Integration** - Choose one:
```scala
// For Specs2
libraryDependencies += "pl.iterators" %% "baklava-specs2" % "VERSION" % Test

// For ScalaTest
libraryDependencies += "pl.iterators" %% "baklava-scalatest" % "VERSION" % Test

// For MUnit
libraryDependencies += "pl.iterators" %% "baklava-munit" % "VERSION" % Test
```

#### Output Format Dependencies (Required)

**Choose one or more output formats:**

```scala
// Simple format (no additional configuration required)
libraryDependencies += "pl.iterators" %% "baklava-simple" % "VERSION" % Test

// OpenAPI format (requires additional configuration - see below)
libraryDependencies += "pl.iterators" %% "baklava-openapi" % "VERSION" % Test

// TS-REST format (requires additional configuration - see below)
libraryDependencies += "pl.iterators" %% "baklava-tsrest" % "VERSION" % Test

// oRPC contract format
libraryDependencies += "pl.iterators" %% "baklava-orpc" % "VERSION" % Test
```

#### Optional: SwaggerUI Support

SwaggerUI is available for both Pekko HTTP and http4s (combined with the OpenAPI output format). Add the matching routes module:

```scala
// Pekko HTTP
libraryDependencies += "pl.iterators" %% "baklava-pekko-http-routes" % "VERSION"

// http4s
libraryDependencies += "pl.iterators" %% "baklava-http4s-routes" % "VERSION"
```

## Configuration

### Basic Configuration

The minimal configuration required in your `build.sbt`:

```scala
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false
  )
)
```

### Format-Specific Configuration

#### OpenAPI Configuration (Required for OpenAPI format)

If you're using the OpenAPI format, add this configuration:

```scala
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false,
    baklavaGenerateConfigs := Map(
      "openapi-info" ->
        s"""
          |{
          |  "openapi" : "3.0.1",
          |  "info" : {
          |    "title" : "My API",
          |    "version" : "1.0.0"
          |  }
          |}
          |""".stripMargin
    )
  )
)
```

#### TS-REST Configuration (Required for TS-REST format)

If you're using the TS-REST format, add this to your `baklavaGenerateConfigs` map:

```scala
baklavaGenerateConfigs := Map(
  "ts-rest-package-contract-json" ->
    """
      |{
      |  "name": "your api package e.g.: @company/backend-contracts",
      |  "version": "VERSION",
      |  "main": "index.js",
      |  "types": "index.d.ts"
      |}
      |""".stripMargin
)
```

#### Combined Configuration Example

If you're using both OpenAPI and TS-REST formats:

```scala
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false,
    baklavaGenerateConfigs := Map(
      "openapi-info" ->
        s"""
          |{
          |  "openapi" : "3.0.1",
          |  "info" : {
          |    "title" : "My API",
          |    "version" : "1.0.0"
          |  }
          |}
          |""".stripMargin,
      "ts-rest-package-contract-json" ->
        """
          |{
          |  "name": "@company/backend-contracts",
          |  "version": "1.0.0",
          |  "main": "index.js",
          |  "types": "index.d.ts"
          |}
          |""".stripMargin
    )
  )
)
```

## Complete Example Configurations

### Example 1: Pekko HTTP + ScalaTest + OpenAPI with SwaggerUI

```scala
// build.sbt
enablePlugins(BaklavaSbtPlugin)

libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-pekko-http" % "VERSION" % Test,
  "pl.iterators" %% "baklava-scalatest" % "VERSION" % Test,
  "pl.iterators" %% "baklava-openapi" % "VERSION" % Test,
  "pl.iterators" %% "baklava-pekko-http-routes" % "VERSION" // For SwaggerUI
)

inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false,
    baklavaGenerateConfigs := Map(
      "openapi-info" ->
        s"""
          |{
          |  "openapi" : "3.0.1",
          |  "info" : {
          |    "title" : "My API",
          |    "version" : "1.0.0"
          |  }
          |}
          |""".stripMargin
    )
  )
)
```

### Example 2: HTTP4s + MUnit + Simple Format

```scala
// build.sbt
enablePlugins(BaklavaSbtPlugin)

libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-http4s" % "VERSION" % Test,
  "pl.iterators" %% "baklava-munit" % "VERSION" % Test,
  "pl.iterators" %% "baklava-simple" % "VERSION" % Test
)

inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false
  )
)
```

To serve SwaggerUI from an http4s app, add `baklava-http4s-routes` and the OpenAPI format:

```scala
libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-http4s" % "VERSION" % Test,
  "pl.iterators" %% "baklava-openapi" % "VERSION" % Test,
  "pl.iterators" %% "baklava-http4s-routes" % "VERSION"
)
```

### Example 3: Pekko HTTP + Specs2 + Multiple Formats

```scala
// build.sbt
enablePlugins(BaklavaSbtPlugin)

libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-pekko-http" % "VERSION" % Test,
  "pl.iterators" %% "baklava-specs2" % "VERSION" % Test,
  "pl.iterators" %% "baklava-simple" % "VERSION" % Test,
  "pl.iterators" %% "baklava-openapi" % "VERSION" % Test,
  "pl.iterators" %% "baklava-tsrest" % "VERSION" % Test,
  "pl.iterators" %% "baklava-orpc" % "VERSION" % Test,
  "pl.iterators" %% "baklava-pekko-http-routes" % "VERSION" // For SwaggerUI
)

inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false,
    baklavaGenerateConfigs := Map(
      "openapi-info" ->
        s"""
          |{
          |  "openapi" : "3.0.1",
          |  "info" : {
          |    "title" : "My API",
          |    "version" : "1.0.0"
          |  }
          |}
          |""".stripMargin,
      "ts-rest-package-contract-json" ->
        """
          |{
          |  "name": "@company/backend-contracts",
          |  "version": "1.0.0",
          |  "main": "index.js",
          |  "types": "index.d.ts"
          |}
          |""".stripMargin
    )
  )
)
```

## SBT Tasks

The plugin provides several SBT tasks:

- `baklavaGenerate` - Generate API documentation from your tests (executed automatically during `sbt test`)
- `baklavaClean` - Clean generated documentation files

The documentation will be generated in `target/baklava/` directory after running your tests:
- Simple format: `target/baklava/simple/`
- OpenAPI format: `target/baklava/openapi/openapi.yml`
- TS-REST format: `target/baklava/tsrest/`
- oRPC contract format: `target/baklava/orpc/`

## Runtime Configuration

### SwaggerUI and Routes Configuration

If you're using SwaggerUI (Pekko HTTP or http4s + OpenAPI), you can configure the routes behavior at runtime. Each module exposes its own `BaklavaRoutesConfig` case class with identical fields — `pl.iterators.baklava.http4s.routes.BaklavaRoutesConfig` for the http4s module and `pl.iterators.baklava.routes.BaklavaRoutesConfig` for the Pekko HTTP module. They are not interchangeable types; import the one that matches the module you're wiring up. The Pekko HTTP module additionally accepts a Typesafe `com.typesafe.config.Config`, since HOCON is the idiomatic config format on that stack.

#### Configuration options at a glance

| Field                      | Default            | Environment variable                 |
| -------------------------- | ------------------ | ------------------------------------ |
| `enabled`                  | `true`             | `BAKLAVA_ROUTES_ENABLED`             |
| `basicAuthUser`            | `None`             | `BAKLAVA_ROUTES_BASIC_AUTH_USER`     |
| `basicAuthPassword`        | `None`             | `BAKLAVA_ROUTES_BASIC_AUTH_PASSWORD` |
| `fileSystemPath`           | `./target/baklava` | `BAKLAVA_ROUTES_FILESYSTEM_PATH`     |
| `publicPathPrefix`         | `/`                | `BAKLAVA_ROUTES_PUBLIC_PATH_PREFIX`  |
| `apiPublicPathPrefix`      | `/v1`              | `BAKLAVA_ROUTES_API_PUBLIC_PATH_PREFIX` |

#### Configuration via case class (both modules)

Calling `BaklavaRoutes.routes()` with no arguments uses defaults overridden by the `BAKLAVA_ROUTES_*` environment variables:

```scala
// http4s
import pl.iterators.baklava.http4s.routes.BaklavaRoutes
val docs: HttpRoutes[IO] = BaklavaRoutes.routes()

// Pekko HTTP
import pl.iterators.baklava.routes.BaklavaRoutes
val docs: Route = BaklavaRoutes.routes()
```

To configure explicitly, build a `BaklavaRoutesConfig` yourself (works well with Ciris, PureConfig, or anything else that produces a case class):

```scala
// http4s
import pl.iterators.baklava.http4s.routes.{BaklavaRoutes, BaklavaRoutesConfig}
val docs: HttpRoutes[IO] = BaklavaRoutes.routes(
  BaklavaRoutesConfig(
    basicAuthUser = Some("admin"),
    basicAuthPassword = Some("secret"),
    publicPathPrefix = "/internal/docs"
  )
)

// Pekko HTTP — same shape, different package
import pl.iterators.baklava.routes.{BaklavaRoutes, BaklavaRoutesConfig}
val docs: Route = BaklavaRoutes.routes(BaklavaRoutesConfig(enabled = false))
```

#### Configuration via application.conf (Pekko HTTP only)

The Pekko HTTP module also accepts a Typesafe `Config`. Add a `baklava-routes` section to `src/main/resources/application.conf`:

```hocon
baklava-routes {
  # Enable/disable baklava routes (default: true)
  # Can be overridden with environment variable: BAKLAVA_ROUTES_ENABLED
  enabled = true

  # HTTP Basic Auth credentials for protecting SwaggerUI (optional)
  # Can be overridden with environment variables: BAKLAVA_ROUTES_BASIC_AUTH_USER, BAKLAVA_ROUTES_BASIC_AUTH_PASSWORD
  # basic-auth-user = "admin"
  # basic-auth-password = "secret"

  # Directory where generated documentation files are stored (default: "./target/baklava")
  # Can be overridden with environment variable: BAKLAVA_ROUTES_FILESYSTEM_PATH
  filesystem-path = "./target/baklava"

  # URL prefix for serving baklava resources (default: "/")
  # Can be overridden with environment variable: BAKLAVA_ROUTES_PUBLIC_PATH_PREFIX
  public-path-prefix = "/"

  # URL prefix for API endpoints in documentation (default: "/v1")
  # Can be overridden with environment variable: BAKLAVA_ROUTES_API_PUBLIC_PATH_PREFIX
  api-public-path-prefix = "/v1"
}
```

```scala
import com.typesafe.config.ConfigFactory
import pl.iterators.baklava.routes.BaklavaRoutes

val docs: Route = BaklavaRoutes.routes(ConfigFactory.load())
```

If you're on http4s and want HOCON, build the case class from your `Config`:

```scala
import com.typesafe.config.{Config, ConfigFactory}
import pl.iterators.baklava.http4s.routes.{BaklavaRoutes, BaklavaRoutesConfig}
import scala.util.Try

val c: Config = ConfigFactory.load().getConfig("baklava-routes")
val docs = BaklavaRoutes.routes(
  BaklavaRoutesConfig(
    enabled = c.getBoolean("enabled"),
    basicAuthUser = Try(c.getString("basic-auth-user")).toOption,
    basicAuthPassword = Try(c.getString("basic-auth-password")).toOption,
    fileSystemPath = c.getString("filesystem-path"),
    publicPathPrefix = c.getString("public-path-prefix"),
    apiPublicPathPrefix = c.getString("api-public-path-prefix")
  )
)
```

#### Configuration via Environment Variables

Either module's no-arg `BaklavaRoutes.routes()` reads these variables on startup:

```bash
# Enable/disable baklava routes
export BAKLAVA_ROUTES_ENABLED=true

# Set basic auth credentials (optional)
export BAKLAVA_ROUTES_BASIC_AUTH_USER=admin
export BAKLAVA_ROUTES_BASIC_AUTH_PASSWORD=secret

# Set custom filesystem path
export BAKLAVA_ROUTES_FILESYSTEM_PATH=/custom/path/to/docs

# Set custom URL prefixes
export BAKLAVA_ROUTES_PUBLIC_PATH_PREFIX=/docs
export BAKLAVA_ROUTES_API_PUBLIC_PATH_PREFIX=/api/v2
```

#### Common Configuration Examples

**Example 1: Development setup with custom paths**
```scala
BaklavaRoutesConfig(
  enabled = true,
  fileSystemPath = "./docs/generated",
  publicPathPrefix = "/api-docs",
  apiPublicPathPrefix = "/api/v1"
)
```

**Example 2: Production setup with basic auth**
```scala
BaklavaRoutesConfig(
  enabled = true,
  basicAuthUser = Some("api-docs"),
  basicAuthPassword = Some("secure-password"),
  publicPathPrefix = "/internal/docs"
)
```

**Example 3: Disabled in production (using environment variable)**
```bash
export BAKLAVA_ROUTES_ENABLED=false
```
