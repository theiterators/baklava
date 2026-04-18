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
        securityScheme.setFlows(buildOAuthFlows(flows))
      }
      scheme.security.oAuth2InCookie.foreach { case OAuth2InCookie(flows, _) =>
        securityScheme.setType(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
        securityScheme.setFlows(buildOAuthFlows(flows))
      }
      scheme.name -> securityScheme
    }

    // Merge generated security schemes into any user-supplied components (e.g. from openapi-info).
    // distinctBy above keeps first; we preserve the first-wins semantics here too by skipping
    // schemes the user has already declared under the same name.
    val components      = Option(openAPI.getComponents).getOrElse(new io.swagger.v3.oas.models.Components())
    val existingSchemes = Option(components.getSecuritySchemes).map(_.asScala.keySet.toSet).getOrElse(Set.empty)
    securitySchemes.foreach { case (name, scheme) =>
      if (!existingSchemes.contains(name)) { val _ = components.addSecuritySchemes(name, scheme) }
    }
    val _ = openAPI.components(components)

    calls.groupBy(_.request.symbolicPath).toList.sortBy(_._1).foreach { case (path, calls) =>
      val pathItem = new io.swagger.v3.oas.models.PathItem()
      calls.groupBy(_.request.method).toList.sortBy(_._1.map(_.method)).foreach { case (method, calls) =>
        val operation          = new io.swagger.v3.oas.models.Operation()
        val operationResponses = new ApiResponses()
        calls.groupBy(_.response.status).toList.sortBy(_._1.code).foreach { case (status, commonStatusCalls) =>
          // One ApiResponse per status; all contentType media types accumulate into it.
          val r       = new io.swagger.v3.oas.models.responses.ApiResponse()
          val content = new Content()

          val mergedResponseDescription =
            commonStatusCalls.flatMap(_.request.responseDescription).distinct.sorted.mkString(" / ")
          if (mergedResponseDescription.nonEmpty) r.setDescription(mergedResponseDescription)

          val mergedResponseHeaders =
            commonStatusCalls
              .flatMap(_.request.responseHeaders)
              .distinctBy(_.name.toLowerCase)
              .filterNot(_.name.toLowerCase == "content-type")
              .sortBy(_.name.toLowerCase)
          mergedResponseHeaders.foreach { header =>
            val h = new io.swagger.v3.oas.models.headers.Header()
            h.schema(baklavaSchemaToOpenAPISchema(header.schema))
            h.setRequired(header.schema.required)
            header.description.foreach(h.setDescription)
            caseInsensitiveHeaderLookup(commonStatusCalls, header.name).foreach(h.example)
            val _ = r.addHeaderObject(header.name, h)
          }

          var anyMediaTypeEmitted = false
          commonStatusCalls
            .groupBy(_.response.responseContentType)
            .toList
            .sortBy(_._1.getOrElse(""))
            .foreach { case (contentType, unsortedContentTypeCalls) =>
              val commonContentTypeCalls = unsortedContentTypeCalls.sortBy(_.request.responseDescription.getOrElse(""))
              val mediaType              = new io.swagger.v3.oas.models.media.MediaType()
              val firstSchema            = commonContentTypeCalls.flatMap(_.response.bodySchema).headOption
              firstSchema.foreach(schema => mediaType.schema(baklavaSchemaToOpenAPISchema(schema)))

              val usedExampleKeys = scala.collection.mutable.Set.empty[String]
              commonContentTypeCalls.zipWithIndex.foreach { case (BaklavaSerializableCall(ctx, response), idx) =>
                val responseStr =
                  if (contentType.contains("application/json"))
                    parse(response.responseBodyString).map(_.printWith(Printer.spaces2)).getOrElse(response.responseBodyString)
                  else response.responseBodyString

                val baseKey   = ctx.responseDescription.getOrElse(s"Example $idx")
                val uniqueKey = disambiguateKey(baseKey, usedExampleKeys)
                val _         = mediaType.addExamples(
                  uniqueKey,
                  new io.swagger.v3.oas.models.examples.Example().value(responseStr)
                )
              }

              if (firstSchema.isDefined) {
                val _ = content.addMediaType(contentType.getOrElse("application/octet-stream"), mediaType)
                anyMediaTypeEmitted = true
              }
            }

          if (anyMediaTypeEmitted) { val _ = r.setContent(content) }
          val _ = operationResponses.addApiResponse(status.code.toString, r)
        }
        val _ = operation.responses(operationResponses)

        // TODO: are we sure? bodyRequest could be moved to METHOD-level as it's defined on the `operation` level in OpenAPI but
        // this would make the DSL less intuitive
        val requestBody = new io.swagger.v3.oas.models.parameters.RequestBody()
        val content     = new Content()

        val successfulCalls    = calls.filter(_.response.status.code / 100 == 2).sortBy(_.response.status.code)
        val responsesToProcess =
          if (successfulCalls.isEmpty) calls else successfulCalls // sometimes there are no successful responses
        responsesToProcess
          .groupBy(_.response.requestContentType)
          .toList
          .sortBy(_._1.getOrElse(""))
          .foreach { case (contentType, unsortedCalls) =>
            val calls       = unsortedCalls.sortBy(_.request.responseDescription.getOrElse(""))
            val firstSchema = calls.flatMap(_.request.bodySchema).headOption
            val hasAnyBody  = calls.exists(_.response.requestBodyString.nonEmpty)

            // Emit a media-type entry when there's either a captured contentType, a schema,
            // or some request body evidence — skipping only the pure "no body at all" case
            // (e.g. GET requests with no payload).
            if (contentType.isDefined || firstSchema.isDefined || hasAnyBody) {
              val mediaType = new io.swagger.v3.oas.models.media.MediaType()
              firstSchema.foreach(schema => mediaType.schema(baklavaSchemaToOpenAPISchema(schema)))

              val usedRequestExampleKeys = scala.collection.mutable.Set.empty[String]
              calls.zipWithIndex.foreach { case (call, idx) =>
                // todo this is wierd that requestBodyString is in response
                if (call.response.requestBodyString.nonEmpty) {
                  val requestStr =
                    if (contentType.contains("application/json"))
                      parse(call.response.requestBodyString).map(_.printWith(Printer.spaces2)).getOrElse(call.response.requestBodyString)
                    else call.response.requestBodyString

                  val baseKey   = call.request.responseDescription.getOrElse(s"Example $idx")
                  val uniqueKey = disambiguateKey(baseKey, usedRequestExampleKeys)
                  val _         = mediaType.addExamples(
                    uniqueKey,
                    new io.swagger.v3.oas.models.examples.Example().value(requestStr)
                  )
                }
              }
              val _ = content.addMediaType(contentType.getOrElse("application/octet-stream"), mediaType)
            }
          }
        val _ = requestBody.setContent(content)
        if (!content.isEmpty) { val _ = operation.setRequestBody(requestBody) }

        val distinctOperationIds = calls.flatMap(_.request.operationId).distinct
        if (distinctOperationIds.size == 1) operation.setOperationId(distinctOperationIds.head)
        val mergedSummary = calls.flatMap(_.request.operationSummary).distinct.mkString(" / ")
        if (mergedSummary.nonEmpty) operation.setSummary(mergedSummary)
        val mergedDescription = calls.flatMap(_.request.operationDescription).distinct.mkString("\n\n")
        if (mergedDescription.nonEmpty) operation.setDescription(mergedDescription)
        operation.setTags(calls.flatMap(_.request.operationTags).distinct.asJava)

        def extractOAuthScopes(flows: OAuthFlows): Seq[String] = {
          (flows.implicitFlow.toList.flatMap(_.scopes.keys) ++
            flows.passwordFlow.toList.flatMap(_.scopes.keys) ++
            flows.authorizationCodeFlow.toList.flatMap(_.scopes.keys) ++
            flows.clientCredentialsFlow.toList.flatMap(_.scopes.keys)).distinct
        }

        val distinctSchemes           = calls.flatMap(_.request.securitySchemes).distinctBy(_.name)
        val hasUnauthenticatedVariant = calls.exists(_.request.securitySchemes.isEmpty)
        val securityRequirements      = distinctSchemes.map { ss =>
          val scopes = ss.security.oAuth2InBearer
            .map(oAuth2 => extractOAuthScopes(oAuth2.flows))
            .orElse(ss.security.oAuth2InCookie.map(oAuth2 => extractOAuthScopes(oAuth2.flows)))
            .getOrElse(Seq.empty)

          new io.swagger.v3.oas.models.security.SecurityRequirement().addList(ss.name, scopes.asJava)
        }
        val finalSecurityRequirements =
          if (hasUnauthenticatedVariant && distinctSchemes.nonEmpty)
            new io.swagger.v3.oas.models.security.SecurityRequirement() +: securityRequirements
          else securityRequirements
        operation.setSecurity(finalSecurityRequirements.asJava)

        // Merge parameter declarations across every call in the operation so variants with different
        // query/path/header sets all contribute. Previously only calls.head's parameters survived.
        val mergedQueryParams = calls.flatMap(_.request.queryParametersSeq).distinctBy(_.name).sortBy(_.name)
        mergedQueryParams
          .map { queryParam =>
            val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
            parameter.setName(queryParam.name)
            parameter.setIn("query")
            parameter.setRequired(queryParam.schema.required)
            parameter.setExplode(true) // I guess this is default?
            parameter.setSchema(baklavaSchemaToOpenAPISchema(queryParam.schema))
            queryParam.description.foreach(parameter.setDescription)
            // Example values from captured test inputs are tracked separately in #68.
            parameter
          }
          .foreach(operation.addParametersItem)

        val mergedPathParams = calls.flatMap(_.request.pathParametersSeq).distinctBy(_.name).sortBy(_.name)
        mergedPathParams
          .map { pathParam =>
            val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
            parameter.setName(pathParam.name)
            parameter.setIn("path")
            // Path params must always be `required: true` per OAS 3.x.
            parameter.setRequired(true)
            parameter.setSchema(baklavaSchemaToOpenAPISchema(pathParam.schema))
            pathParam.description.foreach(parameter.setDescription)
            // Example values from captured test inputs are tracked separately in #68.
            parameter
          }
          .foreach(operation.addParametersItem)

        val mergedHeaders = calls
          .flatMap(_.request.headersSeq)
          .map(h => (h, h.name.toLowerCase(java.util.Locale.ROOT)))
          .distinctBy(_._2)
          .filterNot { case (_, lowered) => lowered == "content-type" || lowered == "accept" || lowered == "authorization" }
          .sortBy(_._2)
          .map(_._1)
        mergedHeaders
          .map { header =>
            val parameter = new io.swagger.v3.oas.models.parameters.Parameter()
            parameter.setName(header.name)
            parameter.setIn("header")
            parameter.setRequired(header.schema.required)
            parameter.setSchema(baklavaSchemaToOpenAPISchema(header.schema))
            header.description.foreach(parameter.setDescription)
            // Example values from captured test inputs are tracked separately in #68.
            parameter
          }
          .foreach(operation.addParametersItem)

        method.foreach { m =>
          pathItem.operation(io.swagger.v3.oas.models.PathItem.HttpMethod.valueOf(m.method.toUpperCase), operation)
        }
        calls.head.request.pathSummary.foreach(pathItem.setSummary)
        calls.head.request.pathDescription.foreach(pathItem.setDescription)
      }
      openAPI.path(path, pathItem)
    }
  }

  private def buildOAuthFlows(flows: OAuthFlows): io.swagger.v3.oas.models.security.OAuthFlows = {
    val oauthFlows = new io.swagger.v3.oas.models.security.OAuthFlows()

    def scopesOf(entries: Map[String, String]): io.swagger.v3.oas.models.security.Scopes = {
      val scopes = new io.swagger.v3.oas.models.security.Scopes()
      entries.foreach { case (name, description) => scopes.addString(name, description) }
      scopes
    }

    flows.implicitFlow.foreach { implicitFlow =>
      val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
      flow.setAuthorizationUrl(implicitFlow.authorizationUrl.toString)
      flow.setScopes(scopesOf(implicitFlow.scopes))
      oauthFlows.setImplicit(flow)
    }
    flows.passwordFlow.foreach { passwordFlow =>
      val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
      flow.setTokenUrl(passwordFlow.tokenUrl.toString)
      flow.setScopes(scopesOf(passwordFlow.scopes))
      oauthFlows.setPassword(flow)
    }
    flows.authorizationCodeFlow.foreach { authorizationCodeFlow =>
      val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
      flow.setAuthorizationUrl(authorizationCodeFlow.authorizationUrl.toString)
      flow.setTokenUrl(authorizationCodeFlow.tokenUrl.toString)
      flow.setScopes(scopesOf(authorizationCodeFlow.scopes))
      oauthFlows.setAuthorizationCode(flow)
    }
    flows.clientCredentialsFlow.foreach { clientCredentialsFlow =>
      val flow = new io.swagger.v3.oas.models.security.OAuthFlow()
      flow.setTokenUrl(clientCredentialsFlow.tokenUrl.toString)
      flow.setScopes(scopesOf(clientCredentialsFlow.scopes))
      oauthFlows.setClientCredentials(flow)
    }

    oauthFlows
  }

  private def disambiguateKey(baseKey: String, used: scala.collection.mutable.Set[String]): String = {
    if (!used.contains(baseKey)) {
      used += baseKey
      baseKey
    } else {
      var suffix = 2
      while (used.contains(s"$baseKey ($suffix)")) suffix += 1
      val candidate = s"$baseKey ($suffix)"
      used += candidate
      candidate
    }
  }

  private def caseInsensitiveHeaderLookup(
      calls: Seq[BaklavaSerializableCall],
      name: String
  ): Option[String] = {
    val lowered = name.toLowerCase
    calls.iterator
      .flatMap(_.response.headers.find(_.name.toLowerCase == lowered).map(_.value))
      .nextOption()
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
