---
sidebar_position: 7
title: Configuration
---

# Configuration

Most of the configuration is already covered in [[installation.md]]. Here are some specific and advance options.

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
