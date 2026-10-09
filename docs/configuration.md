---
sidebar_position: 8
title: Configuration
---

# Configuration

Most of the configuration is already covered in [[installation.md]]. Here are some specific and advanced options.

## Open API info section configuration

In plugin configuration for openAPI you can put anything that is in the Open API spec (https://spec.openapis.org/oas/v3.0.1.html) although only info section is recommended to be used. Other sections (like components) can be overridden by library if specified here. For Open API configuration you can use JSON or YAML format.

Example YAML configuration with all fields specified in version 3.0.1 of Open API specification:
```sbt
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    fork := false,
    baklavaGenerateConfigs := Map(
      "openapi-info" ->
        s"""
          |openapi: 3.0.1
          |info:
          |  title: My API
          |  version: 1.0.0
          |  description: API documentation generated from tests
          |  termsOfService: http://example.com/terms/
          |  contact:
          |    name: API Support
          |    url: http://www.example.com/support
          |    email: api-support@example.com
          |  license:
          |    name: MIT
          |    url: https://opensource.org/licenses/MIT
          |""".stripMargin
    )
  )
)
```

### Open API Post-Processing


The Open API post-processing feature allows you to programmatically modify the generated API documentation. By implementing a post processor, you can customize, enhance, or adjust the OpenAPI spec after it is created. For example, you can add tags, descriptions, or other metadata to endpoints.

Below is an example post processor that defines a "Users" tag and adds it to every API endpoint:

```scala
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.tags.Tag
import pl.iterators.baklava.openapi.BaklavaOpenApiPostProcessor

import scala.jdk.CollectionConverters.*

class TagOpenApiPostProcessor extends BaklavaOpenApiPostProcessor {
  override def process(openAPI: OpenAPI): Unit = {
    // Define the "Users" tag
    val tags = new java.util.ArrayList[Tag]()
    val usersTagName = "Users"
    tags.add(new Tag().name(usersTagName).description("Operations related to users"))
    openAPI.setTags(tags)

    // Add the "Users" tag to all operations
    val paths = openAPI.getPaths
    if (paths != null) {
      paths.keySet().asScala.foreach { path =>
        val pathItem = paths.get(path)
        val ops = pathItem.readOperations
        if (ops != null) {
          ops.asScala.filter(_ != null).foreach { op =>
            val opTags = new java.util.ArrayList[String]()
            opTags.add(usersTagName)
            op.setTags(opTags)
          }
        }
      }
    }
  }
}
```


**Note:**
- To use post-processing, simply implement the `BaklavaOpenApiPostProcessor` trait. No additional registration or configuration is required.
- If you place your post processor in your application's main source directory, make sure to add the `baklava-openapi` dependency to your main project dependencies (not just test dependencies).

#### Narrowing the Post-Processor Classpath Scan

Post-processors are discovered via reflection across the full classpath by default. On large projects this can add seconds of startup latency and may trigger JDK 17+ illegal-access warnings from unrelated JARs.

Set `baklava.postProcessorPackages` in your `baklavaGenerateConfigs` to restrict the scan to a comma-separated list of package prefixes:

```scala
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    baklavaGenerateConfigs := Map(
      "baklava.postProcessorPackages" -> "com.example.openapi,com.example.utils.openapi"
    )
  )
)
```

Leaving the key unset preserves the previous behavior (scan every package). Recommended for any project large enough to notice the scan time.

## Output views

A view is a named subset of the captured calls that every formatter renders once more, next to the full output. The typical uses are a public OpenAPI document that leaves out internal endpoints, and a client contract (ts-rest, oRPC, tsfetch, sttp-client) that leaves out the `/admin` routes so the shipped app never bundles their schemas.

```sbt
inConfig(Test)(
  BaklavaSbtPlugin.settings(Test) ++ Seq(
    baklavaGenerateConfigs := Map(
      "openapi-info" -> """{ ... }""",
      "views" -> """[
        { "name": "public", "filter": "tag-includes:public" },
        { "name": "client", "filter": ["path-excludes:^/admin/", "tag-excludes:internal"] }
      ]"""
    )
  )
)
```

The full output under `target/baklava/<formatter>` is produced as before. Then, for each view, the calls are filtered and every formatter on the classpath runs again with the output root `target/baklava/views/<name>`, so the example above also yields `target/baklava/views/client/openapi/openapi.yml`, `target/baklava/views/client/orpc/…`, and so on for every formatter you depend on. `baklavaClean` removes the views with the rest of `target/baklava`.

Each view has a `name` (letters, digits, `.`, `_`, `-`) and an optional `filter`: one expression, or an array of expressions that must all hold. Without a `filter` the view selects everything. The expressions are:

| Expression               | Keeps a call when                                                        |
| ------------------------ | ------------------------------------------------------------------------ |
| `all`                    | always                                                                   |
| `tag-includes:<tag>`     | the operation declares `<tag>` in its `supports(..., tags = ...)`        |
| `tag-excludes:<tag>`     | the operation does not declare `<tag>`                                   |
| `path-includes:<regex>`  | the regex finds a match in the symbolic path, e.g. `/users/{id}`          |
| `path-excludes:<regex>`  | the regex finds no match in the symbolic path                            |

A view that selects no calls is skipped with a warning, like a run that captured no calls at all; an invalid `views` value fails generation before anything is written.

To serve a view's OpenAPI document through SwaggerUI, mount a second `BaklavaRoutes` with a `BaklavaRoutesConfig` whose `fileSystemPath` points at the view directory, `target/baklava/views/public`; the routes module reads `<fileSystemPath>/openapi/openapi.yml`, which is exactly where the view puts it.

## Test Configuration

### Response Body Truncation

By default, Baklava truncates response bodies in assertion error messages to 8192 characters. You can override this in your base test trait:

```scala
override def maxBodyLengthInAssertion: Int = 16384
```

### In-Memory Capture

For development, you can mix in `BaklavaTestFrameworkDslInMemory` alongside the standard `BaklavaTestFrameworkDsl`. This variant collects test calls in memory rather than serializing each one to disk, and exposes them via `listCalls`. This lets you process calls inline — for example, generating OpenAPI output directly in your test's `afterAll`:

```scala
trait MyInMemorySpec
    extends BaklavaScalatest[Route, ToEntityMarshaller, FromEntityUnmarshaller]
    with BaklavaTestFrameworkDslInMemory[Route, ToEntityMarshaller, FromEntityUnmarshaller, Unit, Unit, ScalatestAsExecution] {

  override def afterAll(): Unit = {
    val calls = listCalls  // Seq[BaklavaSerializableCall]
    // Process calls however you like — generate OpenAPI, dump to console, etc.
  }
}
```

This is useful for quick iteration without running the full SBT plugin pipeline.
