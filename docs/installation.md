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
enablePlugins(BaklavaPlugin)
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
```

#### Optional: SwaggerUI Support

**⚠️ SwaggerUI is only available with Pekko HTTP + OpenAPI format combination**

```scala
// Only add this if you're using Pekko HTTP AND OpenAPI format
libraryDependencies += "pl.iterators" %% "baklava-pekko-http-routes" % "VERSION"
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
enablePlugins(BaklavaPlugin)

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
enablePlugins(BaklavaPlugin)

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

### Example 3: Pekko HTTP + Specs2 + Multiple Formats

```scala
// build.sbt
enablePlugins(BaklavaPlugin)

libraryDependencies ++= Seq(
  "pl.iterators" %% "baklava-pekko-http" % "VERSION" % Test,
  "pl.iterators" %% "baklava-specs2" % "VERSION" % Test,
  "pl.iterators" %% "baklava-simple" % "VERSION" % Test,
  "pl.iterators" %% "baklava-openapi" % "VERSION" % Test,
  "pl.iterators" %% "baklava-tsrest" % "VERSION" % Test,
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

## Runtime Configuration

### SwaggerUI and Routes Configuration

If you're using SwaggerUI (Pekko HTTP + OpenAPI), you can configure the routes behavior at runtime.

#### Configuration via application.conf

Create or update your `src/main/resources/application.conf` file:

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

#### Configuration via Environment Variables

Alternatively, you can configure everything using environment variables:

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
```hocon
baklava-routes {
  enabled = true
  filesystem-path = "./docs/generated"
  public-path-prefix = "/api-docs"
  api-public-path-prefix = "/api/v1"
}
```

**Example 2: Production setup with basic auth**
```hocon
baklava-routes {
  enabled = true
  basic-auth-user = "api-docs"
  basic-auth-password = "secure-password"
  public-path-prefix = "/internal/docs"
}
```

**Example 3: Disabled in production (using environment variable)**
```bash
export BAKLAVA_ROUTES_ENABLED=false
```
