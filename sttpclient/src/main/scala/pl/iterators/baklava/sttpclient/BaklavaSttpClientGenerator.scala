package pl.iterators.baklava.sttpclient

import pl.iterators.baklava.*

/** Translates captured `BaklavaSerializableCall`s to a Scala source tree: one sub-package per operation tag with an `Endpoints.scala` and
  * a `Types.scala`, plus a `common` sub-package with case classes used by two or more tags. Endpoints return sttp-client4 `Request` values
  * — no opinion on effect type, no opinion on JSON library.
  */
private[sttpclient] class BaklavaSttpClientGenerator(basePackage: String, calls: Seq[BaklavaSerializableCall]) {

  private val DefaultTag = "default"

  /** className → rendered case-class field list. */
  private val caseClassBody: Map[String, String] = collectCaseClasses(calls)

  /** className → directly-referenced other named classes (not recursive). */
  private val directRefs: Map[String, Set[String]] = collectDirectRefs(calls)

  /** className → tags whose endpoints reference it (transitively). */
  private val usageByTag: Map[String, Set[String]] = collectUsageByTag(calls)

  /** Classes used by two or more tags → `common/Types.scala`. */
  private val sharedClasses: Set[String] =
    usageByTag.collect { case (name, tags) if tags.size >= 2 => name }.toSet

  /** Classes used by exactly one tag → that tag's `Types.scala`. */
  private val primaryTag: Map[String, String] =
    usageByTag.collect { case (name, tags) if tags.size == 1 => name -> tags.head }.toMap

  /** If any classes land in `common/Types.scala`, render it. Else `None`. */
  def renderSharedTypes: Option[String] = {
    val classes = sharedClasses.toSeq.sorted
    if (classes.isEmpty) None
    else {
      val body = classes.map(cls => s"final case class ${scalaSafeIdent(cls)}(${caseClassBody(cls)})").mkString("\n\n")
      Some(s"package $basePackage.common\n\n$body\n")
    }
  }

  /** Per-tag files. Returns `(relPath, content)` pairs, where `relPath` is relative to the base-package source directory. File names:
    * `{tag}/dtos.scala` holds tag-local case classes, `{tag}/{Tag}Endpoints.scala` holds the `{Tag}Endpoints` object.
    */
  def renderTagFiles: Seq[(String, String)] = {
    val byTag = calls.groupBy(c => c.request.operationTags.headOption.getOrElse(DefaultTag))
    byTag.toSeq.sortBy(_._1).flatMap { case (tag, tagCalls) =>
      val safeTag      = scalaSafeIdent(tag.toLowerCase)
      val endpointsObj = endpointsObjectName(tag)
      val typesFile    = renderTagTypes(tag)
      val endsFile     = renderTagEndpoints(tag, tagCalls)

      val typesEntry = typesFile.map(c => s"$safeTag/dtos.scala" -> c).toSeq
      typesEntry :+ (s"$safeTag/$endpointsObj.scala" -> endsFile)
    }
  }

  private def endpointsObjectName(tag: String): String =
    scalaSafeIdent(capitalize(tag)) + "Endpoints"

  /** Tag's `Types.scala`, or `None` if that tag owns no classes. */
  private def renderTagTypes(tag: String): Option[String] = {
    val tagClasses = primaryTag.collect { case (name, t) if t == tag => name }.toSeq.sorted
    if (tagClasses.isEmpty) None
    else {
      val safeTag = scalaSafeIdent(tag.toLowerCase)
      val refs    = tagClasses.flatMap(directRefs.getOrElse(_, Set.empty)).distinct

      val importLines = new scala.collection.mutable.ListBuffer[String]
      val fromShared  = refs.filter(sharedClasses.contains).sorted
      fromShared.foreach(c => importLines += s"import $basePackage.common.${scalaSafeIdent(c)}")

      val fromOtherTags = refs
        .filter(c => !sharedClasses.contains(c))
        .flatMap(c => primaryTag.get(c).map(ot => c -> ot.toLowerCase))
        .filter { case (_, ot) => scalaSafeIdent(ot) != safeTag }
      fromOtherTags.distinct.foreach { case (c, ot) =>
        importLines += s"import $basePackage.${scalaSafeIdent(ot)}.${scalaSafeIdent(c)}"
      }

      val imports = if (importLines.isEmpty) "" else importLines.mkString("\n") + "\n\n"
      val body    = tagClasses.map(cls => s"final case class ${scalaSafeIdent(cls)}(${caseClassBody(cls)})").mkString("\n\n")

      Some(s"package $basePackage.$safeTag\n\n$imports$body\n")
    }
  }

  /** Tag's `{Tag}Endpoints.scala`. */
  private def renderTagEndpoints(tag: String, tagCalls: Seq[BaklavaSerializableCall]): String = {
    val safeTag    = scalaSafeIdent(tag.toLowerCase)
    val objectName = endpointsObjectName(tag)

    val endpointGroups = tagCalls
      .groupBy(c => (c.request.method.map(_.method.toUpperCase).getOrElse("GET"), c.request.symbolicPath))
      .toSeq
      .sortBy { case ((m, p), _) => (p, m) }

    val endpoints = endpointGroups.map { case (_, endpointCalls) => renderEndpoint(endpointCalls) }

    val needsCirce = endpointGroups.exists { case (_, ec) => endpointUsesCirce(ec) }

    val referenced = tagCalls.flatMap(referencedClassesInCall).distinct

    val needsTypedReq = endpointGroups.exists { case (_, ec) =>
      val cap  = uniformBodyContentType(ec).forall(_.toLowerCase.contains("json"))
      val body = ec.headOption.flatMap(_.request.bodySchema).filterNot(isEmptyBodyInstance)
      cap && body.exists(isTypedBodySchema)
    }

    val importLines = new scala.collection.mutable.ListBuffer[String]
    importLines += "import sttp.client4._"
    if (needsCirce) {
      importLines += "import sttp.client4.circe._"
      // Auto-derive circe codecs for every case class reachable from this file. Pulls in `io.circe.generic` at the use site —
      // users who want fine-grained control can replace this with hand-written or semi-auto codecs on the DTO companions.
      importLines += "import io.circe.generic.auto._"
      if (needsTypedReq) importLines += "import io.circe.syntax._"
    }
    importLines += "import sttp.model.Uri"

    val sharedRefs = referenced.filter(sharedClasses.contains).sorted
    sharedRefs.foreach(c => importLines += s"import $basePackage.common.${scalaSafeIdent(c)}")

    val crossTagRefs = referenced
      .filter(c => !sharedClasses.contains(c))
      .flatMap(c => primaryTag.get(c).map(ot => c -> ot.toLowerCase))
      .filter { case (_, ot) => scalaSafeIdent(ot) != safeTag }
      .distinct
    crossTagRefs.foreach { case (c, ot) => importLines += s"import $basePackage.${scalaSafeIdent(ot)}.${scalaSafeIdent(c)}" }

    s"""package $basePackage.$safeTag
       |
       |${importLines.mkString("\n")}
       |
       |object $objectName {
       |
       |${endpoints.mkString("\n\n")}
       |}
       |""".stripMargin
  }

  // -- endpoint rendering -----------------------------------------------------

  private def renderEndpoint(endpointCalls: Seq[BaklavaSerializableCall]): String = {
    val head          = endpointCalls.head
    val req           = head.request
    val method        = req.method.map(_.method.toUpperCase).getOrElse("GET")
    val fnName        = functionName(req)
    val scaladoc      = renderScaladoc(req)
    val bodyMediaType = uniformBodyContentType(endpointCalls)

    val pathParams  = req.pathParametersSeq
    val queryParams = req.queryParametersSeq
    val declaredHs  = req.headersSeq.filterNot(h => isSpecialHeader(h.name))
    val bodySchema  = req.bodySchema.filterNot(isEmptyBodyInstance)

    // Typed circe bodies are JSON-only; if the capture declared a non-JSON content-type (multipart, form-urlencoded, …), fall back
    // to the raw `bodyJson: String` path so `.contentType(...)` gets emitted and the user can pass the already-encoded payload.
    val captureIsJsonish = bodyMediaType.forall(_.toLowerCase.contains("json"))
    val typedReqSchema   = if (captureIsJsonish) bodySchema.filter(isTypedBodySchema) else None
    val typedRespSchema  = uniformTypedResponseSchema(endpointCalls)

    val pathParamDefs  = pathParams.map(p => s"${scalaSafeIdent(p.name)}: ${scalaType(p.schema)}")
    val queryParamDefs = queryParams.map { p =>
      val t = scalaType(p.schema)
      if (p.schema.required) s"${scalaSafeIdent(p.name)}: $t"
      else s"${scalaSafeIdent(p.name)}: Option[$t] = None"
    }
    val headerParamDefs = declaredHs.map { h =>
      val t = scalaType(h.schema)
      if (h.schema.required) s"${scalaSafeIdent(h.name)}: $t"
      else s"${scalaSafeIdent(h.name)}: Option[$t] = None"
    }
    val bodyParamDef = bodySchema.toSeq.map { s =>
      typedReqSchema match {
        case Some(_) => s"body: ${scalaType(s)}"
        case None    => "bodyJson: String"
      }
    }
    val authParams = securityCredentialParams(req.securitySchemes)

    // Connection-level params (`baseUri`, security credentials) come first; they're invariant across calls and typically set once per
    // session. Per-call params (path / query / headers / body) follow.
    val allParams = Seq("baseUri: Uri") ++ authParams ++ pathParamDefs ++ queryParamDefs ++ headerParamDefs ++ bodyParamDef
    val paramList = allParams.mkString(",\n      ")

    val pathExpr      = renderPathExpression(req.symbolicPath, pathParams.map(_.name))
    val queryAddLines = queryParams.map { p =>
      val name = p.name
      val id   = scalaSafeIdent(name)
      if (p.schema.required) s"""        .addParam("$name", $id.toString)"""
      else s"""        .addParam("$name", $id.map(_.toString))"""
    } ++ securityQueryLines(req.securitySchemes)

    val headerLines = securityHeaderLines(req.securitySchemes) ++
      declaredHs.map { h =>
        val name = h.name
        val id   = scalaSafeIdent(h.name)
        if (h.schema.required) s"""      .header("$name", $id.toString)"""
        else s"""      .header("$name", $id.map(_.toString))"""
      }

    val uriChain = s"$pathExpr${queryAddLines.mkString("\n")}"
    val verbCall = renderVerbCall(method, uriChain)
    val bodyCall = bodySchema match {
      case None    => ""
      case Some(_) =>
        typedReqSchema match {
          // sttp-client4 has no polymorphic `.body[T: BodySerializer]`, so encode via circe to a String and set Content-Type
          // explicitly. Requires `io.circe.syntax._` (for `.asJson`) and an in-scope `Encoder[T]` (via `generic.auto._`).
          case Some(_) =>
            """      .body(body.asJson.noSpaces)
              |      .contentType("application/json")""".stripMargin
          case None =>
            val ct = bodyMediaType.getOrElse("application/json")
            s"""      .body(bodyJson)
               |      .contentType("$ct")""".stripMargin
        }
    }

    val (returnType, responseCall) = typedRespSchema match {
      case Some(s) =>
        val t = scalaType(s)
        (s"Request[Either[ResponseException[String], $t]]", s"      .response(asJson[$t])")
      case None => ("Request[Either[String, String]]", "")
    }

    s"""  $scaladoc
       |  def $fnName(
       |      $paramList
       |  ): $returnType = {
       |    basicRequest
       |      $verbCall
       |${headerLines.mkString("\n")}
       |$bodyCall
       |$responseCall
       |  }""".stripMargin.replaceAll("\n+\\s*\n", "\n")
  }

  /** Does any call in this endpoint group lead to circe-typed request or response code? Kept in sync with `renderEndpoint`. */
  private def endpointUsesCirce(ec: Seq[BaklavaSerializableCall]): Boolean = {
    val bodyMediaType    = uniformBodyContentType(ec)
    val captureIsJsonish = bodyMediaType.forall(_.toLowerCase.contains("json"))
    val bodySchema       = ec.headOption.flatMap(_.request.bodySchema).filterNot(isEmptyBodyInstance)
    val typedReq         = captureIsJsonish && bodySchema.exists(isTypedBodySchema)
    typedReq || uniformTypedResponseSchema(ec).isDefined
  }

  /** A schema is "typed" (i.e. `asJson[T]`-able) when it resolves to a named case class or a collection of one. Primitive/form/empty
    * schemas stay untyped so endpoints with non-JSON bodies keep working.
    */
  private def isTypedBodySchema(schema: BaklavaSchemaSerializable): Boolean = schema.`type` match {
    case SchemaType.ObjectType if isNamedCaseClass(schema) => true
    case SchemaType.ArrayType                              => schema.items.exists(isTypedBodySchema)
    case _                                                 => false
  }

  /** If every 2xx response across an endpoint's captures has a typed body schema AND they all agree on the rendered Scala type, return the
    * common schema; otherwise fall back to the untyped `Either[String, String]` response.
    */
  private def uniformTypedResponseSchema(endpointCalls: Seq[BaklavaSerializableCall]): Option[BaklavaSchemaSerializable] = {
    val successSchemas = endpointCalls
      .filter(c => c.response.status.code >= 200 && c.response.status.code < 300)
      .flatMap(_.response.bodySchema)
      .filterNot(isEmptyBodyInstance)
      .filter(isTypedBodySchema)
    val rendered = successSchemas.map(scalaType).distinct
    if (successSchemas.nonEmpty && rendered.size == 1) successSchemas.headOption else None
  }

  /** If every captured call declared the same non-empty request content-type, return it. */
  private def uniformBodyContentType(endpointCalls: Seq[BaklavaSerializableCall]): Option[String] = {
    val distinct = endpointCalls.flatMap(_.response.requestContentType).distinct
    if (distinct.size == 1) distinct.headOption else None
  }

  /** Well-known verbs get the convenience method (`.get(uri)`); anything else falls back to `.method(Method("X"), uri)` so non-standard
    * HTTP methods still compile.
    */
  private def renderVerbCall(method: String, uriChain: String): String = {
    val upper = method.toUpperCase
    if (BaklavaSttpClientGenerator.KnownMethods.contains(upper)) s".${upper.toLowerCase}($uriChain)"
    else s""".method(sttp.model.Method("$upper"), $uriChain)"""
  }

  private def renderPathExpression(symbolicPath: String, pathParamNames: Seq[String]): String = {
    val ParamSeg = """^\{(.+)\}$""".r
    val segments = symbolicPath.stripPrefix("/").split("/").filter(_.nonEmpty).toSeq
    val parts    = segments.map {
      case ParamSeg(name) if pathParamNames.contains(name) => "s\"$" + scalaSafeIdent(name) + "\""
      case lit                                             => "\"" + lit + "\""
    }
    s"""baseUri.addPath(${parts.mkString(", ")})"""
  }

  private def securityCredentialParams(schemes: Seq[BaklavaSecuritySchemaSerializable]): Seq[String] =
    schemes.headOption.toSeq.flatMap { s =>
      val sec          = s.security
      val tokenName    = scalaSafeIdent(s.name + "Token")
      val usernameName = scalaSafeIdent(s.name + "Username")
      val passwordName = scalaSafeIdent(s.name + "Password")
      val valueName    = scalaSafeIdent(s.name + "Value")
      if (sec.httpBearer.isDefined || sec.oAuth2InBearer.isDefined || sec.openIdConnectInBearer.isDefined)
        Seq(s"$tokenName: String")
      else if (sec.httpBasic.isDefined)
        Seq(s"$usernameName: String", s"$passwordName: String")
      else if (sec.apiKeyInHeader.isDefined || sec.apiKeyInQuery.isDefined || sec.apiKeyInCookie.isDefined)
        Seq(s"$valueName: String")
      else Seq.empty
    }

  private def securityHeaderLines(schemes: Seq[BaklavaSecuritySchemaSerializable]): Seq[String] =
    schemes.headOption.toSeq.flatMap { s =>
      val sec          = s.security
      val tokenName    = scalaSafeIdent(s.name + "Token")
      val usernameName = scalaSafeIdent(s.name + "Username")
      val passwordName = scalaSafeIdent(s.name + "Password")
      val valueName    = scalaSafeIdent(s.name + "Value")
      if (sec.httpBearer.isDefined || sec.oAuth2InBearer.isDefined || sec.openIdConnectInBearer.isDefined)
        Seq(s"""      .header("Authorization", s"Bearer $${$tokenName}")""")
      else if (sec.httpBasic.isDefined)
        Seq(s"""      .auth.basic($usernameName, $passwordName)""")
      else if (sec.apiKeyInHeader.isDefined) {
        val k = sec.apiKeyInHeader.get.name
        Seq(s"""      .header("$k", $valueName)""")
      } else if (sec.apiKeyInCookie.isDefined) {
        val k = sec.apiKeyInCookie.get.name
        Seq(s"""      .cookie("$k", $valueName)""")
      } else Seq.empty
    }

  private def securityQueryLines(schemes: Seq[BaklavaSecuritySchemaSerializable]): Seq[String] =
    schemes.headOption.toSeq.flatMap { s =>
      val sec       = s.security
      val valueName = scalaSafeIdent(s.name + "Value")
      sec.apiKeyInQuery.toSeq.map(k => s"""        .addParam("${k.name}", $valueName)""")
    }

  private def renderScaladoc(req: BaklavaRequestContextSerializable): String = {
    val parts = Seq(req.operationSummary, req.operationDescription).flatten.distinct
    if (parts.isEmpty) "" else s"/** ${parts.mkString(" — ")} */"
  }

  private def functionName(req: BaklavaRequestContextSerializable): String =
    req.operationId.map(scalaSafeIdent).getOrElse {
      val method = req.method.map(_.method.toLowerCase).getOrElse("call")
      method + pascalFromPath(req.symbolicPath)
    }

  private def pascalFromPath(p: String): String =
    p.split("/")
      .filter(_.nonEmpty)
      .map {
        case seg if seg.startsWith("{") && seg.endsWith("}") => "By" + capitalize(seg.substring(1, seg.length - 1))
        case seg                                             => capitalize(seg)
      }
      .mkString

  // -- schema analysis --------------------------------------------------------

  private def collectCaseClasses(calls: Seq[BaklavaSerializableCall]): Map[String, String] = {
    val collected                                      = scala.collection.mutable.LinkedHashMap.empty[String, String]
    def visit(schema: BaklavaSchemaSerializable): Unit = schema.`type` match {
      case SchemaType.ObjectType if isNamedCaseClass(schema) =>
        if (!collected.contains(schema.className)) collected(schema.className) = renderCaseClassBody(schema)
        schema.properties.values.foreach(visit)
      case SchemaType.ObjectType =>
        schema.properties.values.foreach(visit)
      case SchemaType.ArrayType =>
        schema.items.foreach(visit)
      case _ => ()
    }
    calls.foreach { c =>
      c.request.bodySchema.foreach(visit)
      c.response.bodySchema.foreach(visit)
      c.request.pathParametersSeq.foreach(p => visit(p.schema))
      c.request.queryParametersSeq.foreach(p => visit(p.schema))
      c.request.headersSeq.foreach(h => visit(h.schema))
    }
    collected.toMap
  }

  private def collectDirectRefs(calls: Seq[BaklavaSerializableCall]): Map[String, Set[String]] = {
    val byClassName = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
    def findTopLevelRef(s: BaklavaSchemaSerializable): Set[String] = s.`type` match {
      case SchemaType.ObjectType if isNamedCaseClass(s) => Set(s.className)
      case SchemaType.ArrayType                         => s.items.toSet.flatMap(findTopLevelRef)
      case _                                            => Set.empty
    }
    def collectFromSchema(schema: BaklavaSchemaSerializable): Unit = schema.`type` match {
      case SchemaType.ObjectType if isNamedCaseClass(schema) =>
        val refs = schema.properties.values.flatMap(findTopLevelRef).toSet
        byClassName.update(schema.className, byClassName(schema.className) ++ refs)
        schema.properties.values.foreach(collectFromSchema)
      case SchemaType.ObjectType =>
        schema.properties.values.foreach(collectFromSchema)
      case SchemaType.ArrayType => schema.items.foreach(collectFromSchema)
      case _                    => ()
    }
    calls.foreach { c =>
      c.request.bodySchema.foreach(collectFromSchema)
      c.response.bodySchema.foreach(collectFromSchema)
      c.request.pathParametersSeq.foreach(p => collectFromSchema(p.schema))
      c.request.queryParametersSeq.foreach(p => collectFromSchema(p.schema))
      c.request.headersSeq.foreach(h => collectFromSchema(h.schema))
    }
    byClassName.toMap
  }

  private def collectUsageByTag(calls: Seq[BaklavaSerializableCall]): Map[String, Set[String]] = {
    val usage = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
    calls.foreach { c =>
      val tag  = c.request.operationTags.headOption.getOrElse(DefaultTag)
      val refs = referencedClassesInCall(c)
      refs.foreach(cls => usage.update(cls, usage(cls) + tag))
    }
    var changed = true
    while (changed) {
      changed = false
      usage.toMap.foreach { case (cls, tags) =>
        directRefs.getOrElse(cls, Set.empty).foreach { child =>
          val next = usage(child) ++ tags
          if (next != usage(child)) {
            usage.update(child, next)
            changed = true
          }
        }
      }
    }
    usage.toMap
  }

  private def referencedClassesInCall(c: BaklavaSerializableCall): Set[String] = {
    val acc                                       = scala.collection.mutable.Set.empty[String]
    def visit(s: BaklavaSchemaSerializable): Unit = s.`type` match {
      case SchemaType.ObjectType if isNamedCaseClass(s) =>
        if (acc.add(s.className)) s.properties.values.foreach(visit)
      case SchemaType.ObjectType => s.properties.values.foreach(visit)
      case SchemaType.ArrayType  => s.items.foreach(visit)
      case _                     => ()
    }
    c.request.bodySchema.foreach(visit)
    c.response.bodySchema.foreach(visit)
    c.request.pathParametersSeq.foreach(p => visit(p.schema))
    c.request.queryParametersSeq.foreach(p => visit(p.schema))
    c.request.headersSeq.foreach(h => visit(h.schema))
    acc.toSet
  }

  // -- primitives -------------------------------------------------------------

  private def isEmptyBodyInstance(schema: BaklavaSchemaSerializable): Boolean =
    schema.`type` == SchemaType.StringType &&
      schema.`enum`.exists(enums => enums.contains("EmptyBodyInstance") && enums.size == 1)

  private def isNamedCaseClass(schema: BaklavaSchemaSerializable): Boolean =
    schema.`type` == SchemaType.ObjectType &&
      schema.properties.nonEmpty &&
      !schema.className.contains("[") &&
      !Set("FormData", "UrlForm", "Multipart").contains(schema.className)

  private def renderCaseClassBody(schema: BaklavaSchemaSerializable): String = {
    val fields = schema.properties.toSeq.sortBy(_._1).map { case (name, s) =>
      val t = scalaType(s)
      if (s.required) s"${scalaSafeIdent(name)}: $t"
      else s"${scalaSafeIdent(name)}: Option[$t] = None"
    }
    fields.mkString(", ")
  }

  private def scalaType(schema: BaklavaSchemaSerializable): String = schema.`type` match {
    case SchemaType.NullType    => "None.type"
    case SchemaType.BooleanType => "Boolean"
    case SchemaType.IntegerType =>
      schema.format match {
        case Some("int64") => "Long"
        case _             => "Int"
      }
    case SchemaType.NumberType =>
      schema.format match {
        case Some("float")  => "Float"
        case Some("double") => "Double"
        case _              => "BigDecimal"
      }
    case SchemaType.StringType =>
      if (schema.`enum`.exists(_.nonEmpty)) "String"
      else
        schema.format match {
          case Some("uuid") => "java.util.UUID"
          case _            => "String"
        }
    case SchemaType.ArrayType =>
      val inner = schema.items.map(scalaType).getOrElse("Any")
      s"Seq[$inner]"
    case SchemaType.ObjectType =>
      if (isNamedCaseClass(schema)) scalaSafeIdent(schema.className)
      else "Map[String, Any]"
  }

  private def isSpecialHeader(name: String): Boolean =
    Set("authorization", "content-type").contains(name.toLowerCase)

  private def scalaSafeIdent(s: String): String = {
    val cleaned = s.replaceAll("[^A-Za-z0-9_]", "")
    val safe    = if (cleaned.isEmpty) "Anon" else if (cleaned.head.isDigit) "_" + cleaned else cleaned
    if (BaklavaSttpClientGenerator.ReservedIdents.contains(safe)) "`" + safe + "`" else safe
  }

  private def capitalize(s: String): String =
    if (s.isEmpty) s else s"${s.charAt(0).toUpper}${s.substring(1)}"

  def renderReadme: String = {
    val pkgPath = basePackage.replace('.', '/')
    s"""# Baklava-generated sttp-client
       |
       |This directory contains a Scala source tree emitted from your Baklava test cases. It uses
       |[sttp-client4](https://sttp.softwaremill.com) and is framework-agnostic — generated functions
       |return `sttp.client4.Request[R]` values that you `.send(backend)` with any sttp backend
       |(sync, async, fs2, Future, etc.).
       |
       |## Layout
       |
       |- `src/main/scala/$pkgPath/common/dtos.scala` — case classes shared by 2+ tags (omitted if empty)
       |- `src/main/scala/$pkgPath/{tag}/dtos.scala` — tag-local case classes (omitted if empty)
       |- `src/main/scala/$pkgPath/{tag}/{Tag}Endpoints.scala` — one `{Tag}Endpoints` object with a
       |  `def` per endpoint
       |
       |## Typed bodies and responses (circe)
       |
       |Whenever a request body or 2xx response maps to a named case class (or `Seq`/`List` of one),
       |the generated `def` takes that type directly (`body: MyRequest`) and returns
       |`Request[Either[ResponseException[String], MyResponse]]`. The file imports
       |`sttp.client4.circe._` and uses `asJson[T]` / circe's implicit `BodySerializer[T]` — you need
       |to provide circe `Encoder`/`Decoder` instances in scope (e.g. via
       |`io.circe.generic.auto._`).
       |
       |Endpoints whose body/response isn't a named schema (multipart, plain-text, empty) keep the
       |raw `bodyJson: String` input and the `Either[String, String]` response, so you can still
       |use them without circe.
       |
       |## Dependencies
       |
       |```scala
       |libraryDependencies ++= Seq(
       |  "com.softwaremill.sttp.client4" %% "core"  % "4.x.y",
       |  "com.softwaremill.sttp.client4" %% "circe" % "4.x.y",
       |  "io.circe"                      %% "circe-core"    % "0.14.x",
       |  "io.circe"                      %% "circe-generic" % "0.14.x"  // for `generic.auto._`
       |)
       |```
       |
       |## Usage
       |
       |```scala
       |import sttp.client4._
       |import sttp.client4.circe._
       |import sttp.model.Uri
       |import io.circe.generic.auto._
       |// import $basePackage.<tag>.<Tag>Endpoints
       |// import $basePackage.<tag>.dtos._
       |
       |val backend = DefaultSyncBackend()
       |val base    = uri"https://api.example.com"
       |
       |// val req = <Tag>Endpoints.<operation>(baseUri = base /*, typed body + params */)
       |// val res = req.send(backend)  // Either[ResponseException[String], T]
       |```
       |""".stripMargin
  }
}

private[sttpclient] object BaklavaSttpClientGenerator {

  /** HTTP methods exposed as convenience builders on sttp-client4 `basicRequest`. Any verb outside this set goes through
    * `.method(Method(...), uri)` so unusual methods (PROPFIND, PURGE, custom extensions) still compile.
    */
  val KnownMethods: Set[String] = Set("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")

  val ReservedIdents: Set[String] = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "if",
    "implicit",
    "import",
    "lazy",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "try",
    "true",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
    "then",
    "given",
    "enum",
    "export",
    "using",
    "inline",
    "opaque",
    "transparent",
    "derives"
  )
}
