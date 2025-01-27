package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.{Content, Schema}
import pl.iterators.baklava.*
import pl.iterators.baklava.Schema as BaklavaSchema

import scala.jdk.CollectionConverters.*

object OpenAPIGenerator {
  def merge(openAPI: OpenAPI, chunks: List[OpenAPI]): OpenAPI = {
    chunks.foreach { chunk =>
      val chunkPaths = chunk.getPaths
      Option(chunk.getPaths).map(_.keySet().asScala).getOrElse(Set.empty).foreach { chunkPathKey =>
        openAPI.path(chunkPathKey, chunkPaths.get(chunkPathKey))
      }
    }

    val securitySchemesMerged = chunks
      .map { chunk =>
        Option(chunk.getComponents).flatMap(c => Option(c.getSecuritySchemes)).map(_.asScala).getOrElse(Map.empty)
      }
      .foldLeft(Map.empty[String, io.swagger.v3.oas.models.security.SecurityScheme]) { (acc, securitySchemes) =>
        acc ++ securitySchemes
      }
    openAPI.components(new io.swagger.v3.oas.models.Components().securitySchemes(securitySchemesMerged.asJava))

    BaklavaOpenApiPostProcessor.postProcessors.foldLeft(openAPI) { case (openAPI, processor) => processor.process(openAPI) }
  }

  def chunk(context: List[(BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]): OpenAPI = {
    val openAPI = new OpenAPI()

    val securitySchemes = context.flatMap(_._1.securitySchemes).distinctBy(_.name).map { scheme =>
      // TODO: massive repetition and boilerplate ahead, to be refactored
      val securityScheme = new io.swagger.v3.oas.models.security.SecurityScheme()
      scheme.security.descriptionParsed.foreach(securityScheme.setDescription)
      scheme.security match {
        case HttpBearer(bearerFormat, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
          securityScheme.setScheme("bearer")
          securityScheme.setBearerFormat(bearerFormat)
        case HttpBasic(_) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
          securityScheme.setScheme("basic")
        case ApiKeyInHeader(name, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
          securityScheme.setIn(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
          securityScheme.setName(name)
        case ApiKeyInQuery(name, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
          securityScheme.setIn(io.swagger.v3.oas.models.security.SecurityScheme.In.QUERY)
          securityScheme.setName(name)
        case ApiKeyInCookie(name, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
          securityScheme.setIn(io.swagger.v3.oas.models.security.SecurityScheme.In.COOKIE)
          securityScheme.setName(name)
        case MutualTls(_) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.MUTUALTLS)
        case OpenIdConnectInBearer(openIdConnectUrl, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OPENIDCONNECT)
          securityScheme.setOpenIdConnectUrl(openIdConnectUrl.toString)
        case OpenIdConnectInCookie(openIdConnectUrl, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OPENIDCONNECT)
          securityScheme.setOpenIdConnectUrl(openIdConnectUrl.toString)
        case OAuth2InBearer(flows, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
          val oauthFlows = new io.swagger.v3.oas.models.security.OAuthFlows()
          flows.implicitFlow.foreach { implicitFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setAuthorizationUrl(implicitFlow.authorizationUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            implicitFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setImplicit(flow)
          }
          flows.passwordFlow.foreach { passwordFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setTokenUrl(passwordFlow.tokenUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            passwordFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setPassword(flow)
          }
          flows.authorizationCodeFlow.foreach { authorizationCodeFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setAuthorizationUrl(authorizationCodeFlow.authorizationUrl.toString)
            flow.setTokenUrl(authorizationCodeFlow.tokenUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            authorizationCodeFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setAuthorizationCode(flow)
          }
          flows.clientCredentialsFlow.foreach { clientCredentialsFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setTokenUrl(clientCredentialsFlow.tokenUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            clientCredentialsFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setClientCredentials(flow)
          }
          securityScheme.setFlows(oauthFlows)
        case OAuth2InCookie(flows, _) =>
          securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
          val oauthFlows = new io.swagger.v3.oas.models.security.OAuthFlows()
          flows.implicitFlow.foreach { implicitFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setAuthorizationUrl(implicitFlow.authorizationUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            implicitFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setImplicit(flow)
          }
          flows.passwordFlow.foreach { passwordFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setTokenUrl(passwordFlow.tokenUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            passwordFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setPassword(flow)
          }
          flows.authorizationCodeFlow.foreach { authorizationCodeFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setAuthorizationUrl(authorizationCodeFlow.authorizationUrl.toString)
            flow.setTokenUrl(authorizationCodeFlow.tokenUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            authorizationCodeFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setAuthorizationCode(flow)
          }
          flows.clientCredentialsFlow.foreach { clientCredentialsFlow =>
            val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
            flow.setTokenUrl(clientCredentialsFlow.tokenUrl.toString)
            val scopes = new io.swagger.v3.oas.models.security.Scopes()
            clientCredentialsFlow.scopes.foreach { case (name, description) =>
              scopes.addString(name, description)
            }
            flow.setScopes(scopes)
            oauthFlows.setClientCredentials(flow)
          }
          securityScheme.setFlows(oauthFlows)
        case NoopSecurity =>
      }
      scheme.name -> securityScheme
    }
    openAPI.components(new io.swagger.v3.oas.models.Components().securitySchemes(securitySchemes.toMap.asJava))

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

        operation.setSecurity(responses.head._1.securitySchemes.map { ss =>
          val scopes = ss.security match {
            case NoopSecurity                => Seq.empty
            case HttpBearer(_, _)            => Seq.empty
            case HttpBasic(_)                => Seq.empty
            case ApiKeyInHeader(_, _)        => Seq.empty
            case ApiKeyInQuery(_, _)         => Seq.empty
            case ApiKeyInCookie(_, _)        => Seq.empty
            case MutualTls(_)                => Seq.empty
            case OpenIdConnectInBearer(_, _) => Seq.empty
            case OpenIdConnectInCookie(_, _) => Seq.empty
            case OAuth2InBearer(flows, _) =>
              (flows.implicitFlow.toList.flatMap(_.scopes.keys) ++ flows.passwordFlow.toList
                .flatMap(_.scopes.keys) ++ flows.authorizationCodeFlow.toList.flatMap(_.scopes.keys) ++ flows.clientCredentialsFlow.toList
                .flatMap(_.scopes.keys)).distinct
            case OAuth2InCookie(flows, _) =>
              (flows.implicitFlow.toList.flatMap(_.scopes.keys) ++ flows.passwordFlow.toList
                .flatMap(_.scopes.keys) ++ flows.authorizationCodeFlow.toList.flatMap(_.scopes.keys) ++ flows.clientCredentialsFlow.toList
                .flatMap(_.scopes.keys)).distinct
          }
          new io.swagger.v3.oas.models.security.SecurityRequirement().addList(ss.name, scopes.asJava)
        }.asJava)

        responses.head._1.queryParametersSeq
          .map { queryParam =>
            val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
            parameter.setName(queryParam.name)
            parameter.setIn("query")
            parameter.setRequired(queryParam.schema.required)
            parameter.setExplode(true) // I guess this is default?
            parameter.setSchema(baklavaSchemaToOpenAPISchema(queryParam.schema))
            queryParam.description.foreach(parameter.setDescription)
            // TODO: we could add example best on provided in test case :shrug:
            parameter
          }
          .foreach(operation.addParametersItem)

        responses.head._1.pathParametersSeq
          .map { pathParam =>
            val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
            parameter.setName(pathParam.name)
            parameter.setIn("path")
            parameter.setRequired(pathParam.schema.required)
            parameter.setSchema(baklavaSchemaToOpenAPISchema(pathParam.schema))
            pathParam.description.foreach(parameter.setDescription)
            // TODO: we could add example best on provided in test case :shrug:
            parameter
          }
          .foreach(operation.addParametersItem)

        responses.head._1.headersSeq
          .filter(h => h.name.toLowerCase != "content-type" && h.name.toLowerCase != "accept" && h.name.toLowerCase != "authorization")
          .map { header =>
            val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
            parameter.setName(header.name)
            parameter.setIn("header")
            parameter.setRequired(header.schema.required)
            parameter.setSchema(baklavaSchemaToOpenAPISchema(header.schema))
            header.description.foreach(parameter.setDescription)
            // TODO: we could add example best on provided in test case :shrug:
            parameter
          }
          .foreach(operation.addParametersItem)

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
