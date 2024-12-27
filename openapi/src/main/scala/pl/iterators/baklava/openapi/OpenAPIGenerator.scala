package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.{Content, Schema}
import pl.iterators.baklava.*
import pl.iterators.baklava.Schema as BaklavaSchema

import scala.jdk.CollectionConverters.*

object OpenAPIGenerator {
  def merge(chunks: List[OpenAPI]): OpenAPI = {
    val openAPI = new OpenAPI() // TODO: this needs to be providable somehow by the end user
      .info(
        new io.swagger.v3.oas.models.info.Info()
          .title("Swagger Petstore")
          .version("1.0.7")
          .description(
            "This is a sample server Petstore server.  You can find out more about Swagger at [http://swagger.io](http://swagger.io) or on [irc.freenode.net, #swagger](http://swagger.io/irc/).  For this sample, you can use the api key `special-key` to test the authorization filters."
          )
          .termsOfService("http://swagger.io/terms/")
          .contact(new io.swagger.v3.oas.models.info.Contact().email("apiteam@swagger.io"))
          .license(
            new io.swagger.v3.oas.models.info.License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0.html")
          )
      )
      .addServersItem(new io.swagger.v3.oas.models.servers.Server().url("https://petstore3.swagger.io/api/v3"))

    chunks.foreach { chunk =>
      val chunkPaths = chunk.getPaths
      chunkPaths.keySet().asScala.foreach { chunkPathKey =>
        openAPI.path(chunkPathKey, chunkPaths.get(chunkPathKey))
      }
    }

    openAPI
  }

  def chunk(context: List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]): OpenAPI = {
    val openAPI = new OpenAPI()
    context.groupBy(_._1.symbolicPath).foreach { case (path, responses) =>
      val pathItem = new io.swagger.v3.oas.models.PathItem()
      responses.groupBy(_._1.method).foreach { case (method, responses) =>
        val operation          = new io.swagger.v3.oas.models.Operation()
        val operationResponses = new io.swagger.v3.oas.models.responses.ApiResponses()
        responses.groupBy(_._2.status).foreach { case (status, commonStatusResponses) =>
          commonStatusResponses.groupBy(_._2.responseContentType).foreach { case (contentType, commonContentTypeResponses) =>
            val r           = new io.swagger.v3.oas.models.responses.ApiResponse()
            val content     = new Content()
            val mediaType   = new io.swagger.v3.oas.models.media.MediaType()
            val firstSchema = commonContentTypeResponses.flatMap(_._2.bodySchema).find(_ != BaklavaSchema.emptyBodySchema)
            firstSchema.foreach { schema =>
              mediaType.schema(baklavaSchemaToOpenAPISchema(schema))
            }
            commonContentTypeResponses.zipWithIndex.foreach { case ((ctx, response), idx) =>
              mediaType.addExamples(
                ctx.responseDescription.getOrElse(s"Example $idx"),
                new io.swagger.v3.oas.models.examples.Example().value(response.responseBodyString)
              )
            }
            commonStatusResponses.head._1.responseDescription.foreach(r.setDescription)
            commonContentTypeResponses.head._2.headers.headers.filterNot { case (name, _) => name.toLowerCase == "content-type" }.foreach {
              case (name, header) =>
                val h = new io.swagger.v3.oas.models.headers.Header()
                h.schema(new Schema[String]().`type`("string"))
                h.example(header)
                r.addHeaderObject(name, h)
            }
            content.addMediaType(contentType.getOrElse("application/octet-stream"), mediaType)
            r.setContent(content)
            operationResponses.addApiResponse(status.status.toString, r)
          }
        }
        operation.responses(operationResponses)

        // TODO: are we sure? bodyRequest could be moved to METHOD-level as it's defined on the `operation` level in OpenAPI but
        // this would make the DSL less intuitive
        val requestBody = new io.swagger.v3.oas.models.parameters.RequestBody()
        val content     = new Content()

        val successfulResponses = responses.filter(_._2.status.status / 100 == 2).sortBy(_._2.status.status)
        val responsesToProcess =
          if (successfulResponses.isEmpty) responses else successfulResponses // sometimes there are no successful responses
        responsesToProcess.groupBy(_._2.requestContentType).foreach { case (contentType, responses) =>
          val mediaType   = new io.swagger.v3.oas.models.media.MediaType()
          val firstSchema = responses.flatMap(_._1.bodySchema).find(_ != BaklavaSchema.emptyBodySchema)
          firstSchema.foreach { schema =>
            mediaType.schema(baklavaSchemaToOpenAPISchema(schema))
            responses.zipWithIndex.foreach { case ((ctx, response), idx) =>
              mediaType.addExamples(
                ctx.responseDescription.getOrElse(s"Example $idx"),
                new io.swagger.v3.oas.models.examples.Example().value(response.requestBodyString)
              )
            }
            content.addMediaType(contentType.getOrElse("application/octet-stream"), mediaType)
          }
        }
        requestBody.setContent(content)
        if (!content.isEmpty) operation.setRequestBody(requestBody)

        responses.head._1.operationId.foreach(operation.setOperationId)
        responses.head._1.operationSummary.foreach(operation.setSummary)
        responses.head._1.operationDescription.foreach(operation.setDescription)
        operation.setTags(responses.head._1.operationTags.asJava)

        operation.setParameters(responses.head._1.queryParametersSeq.map { queryParam =>
          val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
          parameter.setName(queryParam.name)
          parameter.setIn("query")
          parameter.setRequired(queryParam.schema.required)
          parameter.setExplode(true) // I guess this is default?
          parameter.setSchema(baklavaSchemaToOpenAPISchema(queryParam.schema))
          queryParam.description.foreach(parameter.setDescription)
          // TODO: we could add example best on provided in test case :shrug:
          parameter
        }.asJava)

        pathItem.operation(io.swagger.v3.oas.models.PathItem.HttpMethod.valueOf(method.get.value.toUpperCase), operation)
        responses.head._1.pathSummary.foreach(pathItem.setSummary)
        responses.head._1.pathDescription.foreach(pathItem.setDescription)
      }
      openAPI.path(path, pathItem)
    }
    openAPI
  }

  private def baklavaSchemaToOpenAPISchema(baklavaSchema: BaklavaSchema[?]): Schema[?] = {
    val schema = new Schema[String]()
    schema.set$id(baklavaSchema.className)
    baklavaSchema.`type` match {
      case SchemaType.NullType    => schema.setType("null")
      case SchemaType.StringType  => schema.setType("string")
      case SchemaType.BooleanType => schema.setType("boolean")
      case SchemaType.IntegerType => schema.setType("integer")
      case SchemaType.NumberType  => schema.setType("number")
      case SchemaType.ArrayType   => schema.setType("array")
      case SchemaType.ObjectType  => schema.setType("object")
    }
    baklavaSchema.description.foreach(schema.setDescription)
    baklavaSchema.format.foreach(schema.setFormat)
    baklavaSchema.items.foreach(bs => schema.setItems(baklavaSchemaToOpenAPISchema(bs)))
    baklavaSchema.properties.foreach { case (name, bs) =>
      schema.addProperty(name, baklavaSchemaToOpenAPISchema(bs))
    }
    baklavaSchema.`enum`.foreach(e => schema.setEnum(e.toList.asJava))
    baklavaSchema.default.foreach {
      case None        => schema.setDefault(null)  // special case for Option - not sure if there are any other
      case Some(value) => schema.setDefault(value) // special case for Option - not sure if there are any other
      case value       => schema.setDefault(value)
    }
    schema.setRequired(baklavaSchema.properties.toList.filter(_._2.required).map(_._1).asJava)
    if (baklavaSchema.additionalProperties) schema.setAdditionalProperties(baklavaSchema.additionalProperties)

    schema
  }
}
