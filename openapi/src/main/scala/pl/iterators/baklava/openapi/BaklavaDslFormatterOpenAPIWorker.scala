package pl.iterators.baklava.openapi

import io.circe.Printer
import io.circe.parser.*
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.{Content, Schema}
import io.swagger.v3.oas.models.responses.ApiResponses
import pl.iterators.baklava.*

import scala.jdk.CollectionConverters.*

object BaklavaDslFormatterOpenAPIWorker {
  def generateForCalls(openAPI: OpenAPI, calls: Seq[BaklavaSerializableCall]): Unit = {
    val securitySchemes = calls.flatMap(_.request.securitySchemes).distinctBy(_.name).map { scheme =>
      // TODO: massive repetition and boilerplate ahead, to be refactored
      val securityScheme = new io.swagger.v3.oas.models.security.SecurityScheme()
      scheme.security.descriptionParsed.foreach(securityScheme.setDescription)
      scheme.security.httpBearer.foreach { case HttpBearer(bearerFormat, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
        securityScheme.setScheme("bearer")
        securityScheme.setBearerFormat(bearerFormat)
      }
      scheme.security.httpBasic.foreach { case HttpBasic(_) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
        securityScheme.setScheme("basic")
      }
      scheme.security.apiKeyInHeader.foreach { case ApiKeyInHeader(name, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
        securityScheme.setIn(io.swagger.v3.oas.models.security.SecurityScheme.In.HEADER)
        securityScheme.setName(name)
      }
      scheme.security.apiKeyInQuery.foreach { case ApiKeyInQuery(name, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
        securityScheme.setIn(io.swagger.v3.oas.models.security.SecurityScheme.In.QUERY)
        securityScheme.setName(name)
      }
      scheme.security.apiKeyInCookie.foreach { case ApiKeyInCookie(name, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.APIKEY)
        securityScheme.setIn(io.swagger.v3.oas.models.security.SecurityScheme.In.COOKIE)
        securityScheme.setName(name)
      }
      scheme.security.mutualTls.foreach { case MutualTls(_) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.MUTUALTLS)
      }
      scheme.security.openIdConnectInBearer.foreach { case OpenIdConnectInBearer(openIdConnectUrl, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OPENIDCONNECT)
        securityScheme.setOpenIdConnectUrl(openIdConnectUrl.toString)
      }
      scheme.security.openIdConnectInCookie.foreach { case OpenIdConnectInCookie(openIdConnectUrl, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OPENIDCONNECT)
        securityScheme.setOpenIdConnectUrl(openIdConnectUrl.toString)
      }
      scheme.security.oAuth2InBearer.foreach { case OAuth2InBearer(flows, _) =>
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
      }
      scheme.security.oAuth2InCookie.foreach { case OAuth2InCookie(flows, _) =>
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
      }
      scheme.name -> securityScheme
    }
    openAPI.components(new io.swagger.v3.oas.models.Components().securitySchemes(securitySchemes.toMap.asJava))

    calls.groupBy(_.request.symbolicPath).toList.sortBy(_._1).foreach { case (path, calls) =>
      val pathItem = new io.swagger.v3.oas.models.PathItem()
      calls.groupBy(_.request.method).toList.sortBy(_._1.map(_.value)).foreach { case (method, calls) =>
        val operation          = new io.swagger.v3.oas.models.Operation()
        val operationResponses = new ApiResponses()
        calls.groupBy(_.response.status).toList.sortBy(_._1.status).foreach { case (status, commonStatusCalls) =>
          commonStatusCalls.groupBy(_.response.responseContentType).foreach { case (contentType, commonContentTypeCalls) =>
            val r           = new io.swagger.v3.oas.models.responses.ApiResponse()
            val content     = new Content()
            val mediaType   = new io.swagger.v3.oas.models.media.MediaType()
            val firstSchema = commonContentTypeCalls.flatMap(_.response.bodySchema).headOption
            firstSchema.foreach { schema =>
              mediaType.schema(baklavaSchemaToOpenAPISchema(schema))
            }
            commonContentTypeCalls.zipWithIndex.foreach { case (BaklavaSerializableCall(ctx, response), idx) =>
              val responseStr =
                if (contentType.contains("application/json"))
                  parse(response.responseBodyString).map(_.printWith(Printer.spaces2)).getOrElse(response.responseBodyString)
                else response.responseBodyString

              mediaType.addExamples(
                ctx.responseDescription.getOrElse(s"Example $idx"),
                new io.swagger.v3.oas.models.examples.Example().value(responseStr)
              )
            }
            commonStatusCalls.head.request.responseDescription.foreach(r.setDescription)
            commonContentTypeCalls.head.request.responseHeaders.filterNot { _.name == "content-type" }.foreach { header =>
              val h = new io.swagger.v3.oas.models.headers.Header()
              h.schema(baklavaSchemaToOpenAPISchema(header.schema))
              h.setRequired(header.schema.required)
              header.description.foreach(h.setDescription)
              h.example(commonContentTypeCalls.head.response.headers.headers(header.name)) // TODO: make case-insensitive
              r.addHeaderObject(header.name, h)
            }
            content.addMediaType(contentType.getOrElse("application/octet-stream"), mediaType)
            if (firstSchema.isDefined)
              r.setContent(content)
            operationResponses.addApiResponse(status.status.toString, r)
          }
        }
        operation.responses(operationResponses)

        // TODO: are we sure? bodyRequest could be moved to METHOD-level as it's defined on the `operation` level in OpenAPI but
        // this would make the DSL less intuitive
        val requestBody = new io.swagger.v3.oas.models.parameters.RequestBody()
        val content     = new Content()

        val successfulCalls = calls.filter(_.response.status.status / 100 == 2).sortBy(_.response.status.status)
        val responsesToProcess =
          if (successfulCalls.isEmpty) calls else successfulCalls // sometimes there are no successful responses
        responsesToProcess.groupBy(_.response.requestContentType).foreach { case (contentType, calls) =>
          val mediaType   = new io.swagger.v3.oas.models.media.MediaType()
          val firstSchema = calls.flatMap(_.request.bodySchema).headOption
          firstSchema.foreach { schema =>
            mediaType.schema(baklavaSchemaToOpenAPISchema(schema))
            calls.zipWithIndex.foreach { case (call, idx) =>
              // todo this is wierd that requestBodyString is in response
              val requestStr =
                if (contentType.contains("application/json"))
                  parse(call.response.requestBodyString).map(_.printWith(Printer.spaces2)).getOrElse(call.response.requestBodyString)
                else call.response.requestBodyString

              mediaType.addExamples(
                call.request.responseDescription.getOrElse(s"Example $idx"),
                new io.swagger.v3.oas.models.examples.Example().value(requestStr)
              )
            }
            content.addMediaType(contentType.getOrElse("application/octet-stream"), mediaType)
          }
        }
        requestBody.setContent(content)
        if (!content.isEmpty) operation.setRequestBody(requestBody)

        calls.head.request.operationId.foreach(operation.setOperationId)
        calls.head.request.operationSummary.foreach(operation.setSummary)
        calls.head.request.operationDescription.foreach(operation.setDescription)
        operation.setTags(calls.head.request.operationTags.asJava)

        operation.setSecurity(calls.head.request.securitySchemes.map { ss =>
          def extractOAuthScopes(flows: OAuthFlows): Seq[String] = {
            (flows.implicitFlow.toList.flatMap(_.scopes.keys) ++
              flows.passwordFlow.toList.flatMap(_.scopes.keys) ++
              flows.authorizationCodeFlow.toList.flatMap(_.scopes.keys) ++
              flows.clientCredentialsFlow.toList.flatMap(_.scopes.keys)).distinct
          }

          val scopes = ss.security.oAuth2InBearer
            .map(oAuth2 => extractOAuthScopes(oAuth2.flows))
            .orElse(ss.security.oAuth2InCookie.map(oAuth2 => extractOAuthScopes(oAuth2.flows)))
            .getOrElse(Seq.empty)

          new io.swagger.v3.oas.models.security.SecurityRequirement().addList(ss.name, scopes.asJava)
        }.asJava)

        calls.head.request.queryParametersSeq
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

        calls.head.request.pathParametersSeq
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

        calls.head.request.headersSeq
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
        calls.head.request.pathSummary.foreach(pathItem.setSummary)
        calls.head.request.pathDescription.foreach(pathItem.setDescription)
      }
      openAPI.path(path, pathItem)
    }
  }

  private def baklavaSchemaToOpenAPISchema(baklavaSchema: BaklavaSchemaSerializable): Schema[?] = {
    val schema = new Schema[String]()
    if (baklavaSchema.`type` == SchemaType.ObjectType) {
      schema.extensions(
        Map
          .apply[String, AnyRef](
            "x-class" -> baklavaSchema.className
          )
          .asJava
      )
      ()
    }
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
    baklavaSchema.default.foreach(schema.setDefault)
    schema.setRequired(baklavaSchema.properties.toList.filter(_._2.required).map(_._1).asJava)
    if (baklavaSchema.additionalProperties) schema.setAdditionalProperties(baklavaSchema.additionalProperties)

    schema
  }
}
