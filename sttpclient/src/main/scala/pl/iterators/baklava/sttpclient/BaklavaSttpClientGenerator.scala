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

  /** Per-tag files. Returns `(relPath, content)` pairs, where `relPath` is relative to the base-package source directory. */
  def renderTagFiles: Seq[(String, String)] = {
    val byTag = calls.groupBy(c => c.request.operationTags.headOption.getOrElse(DefaultTag))
    byTag.toSeq.sortBy(_._1).flatMap { case (tag, tagCalls) =>
      val safeTag   = scalaSafeIdent(tag.toLowerCase)
      val typesFile = renderTagTypes(tag)
      val endsFile  = renderTagEndpoints(tag, tagCalls)

      val typesEntry = typesFile.map(c => s"$safeTag/Types.scala" -> c).toSeq
      typesEntry :+ (s"$safeTag/Endpoints.scala" -> endsFile)
    }
  }

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

  /** Tag's `Endpoints.scala`. */
  private def renderTagEndpoints(tag: String, tagCalls: Seq[BaklavaSerializableCall]): String = {
    val safeTag    = scalaSafeIdent(tag.toLowerCase)
    val objectName = scalaSafeIdent(capitalize(tag)) + "Endpoints"

    val endpoints = tagCalls
      .groupBy(c => (c.request.method.map(_.method.toUpperCase).getOrElse("GET"), c.request.symbolicPath))
      .toSeq
      .sortBy { case ((m, p), _) => (p, m) }
      .map { case (_, endpointCalls) => renderEndpoint(endpointCalls) }

    val referenced = tagCalls.flatMap(referencedClassesInCall).distinct

    val importLines = new scala.collection.mutable.ListBuffer[String]
    importLines += "import sttp.client4._"
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

  // -- endpoint rendering (unchanged) -----------------------------------------

  private def renderEndpoint(endpointCalls: Seq[BaklavaSerializableCall]): String = {
    val head     = endpointCalls.head
    val req      = head.request
    val method   = req.method.map(_.method.toUpperCase).getOrElse("GET")
    val fnName   = functionName(req)
    val scaladoc = renderScaladoc(req)

    val pathParams  = req.pathParametersSeq
    val queryParams = req.queryParametersSeq
    val declaredHs  = req.headersSeq.filterNot(h => isSpecialHeader(h.name))
    val bodySchema  = req.bodySchema.filterNot(isEmptyBodyInstance)

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
    val bodyParamDef = bodySchema.toSeq.map(_ => "bodyJson: String")
    val authParams   = securityCredentialParams(req.securitySchemes)

    val allParams = pathParamDefs ++ queryParamDefs ++ headerParamDefs ++ bodyParamDef ++ authParams ++ Seq("baseUri: Uri")
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

    val methodCall = method.toLowerCase
    val verbCall   = s""".$methodCall($pathExpr${queryAddLines.mkString("\n")})"""
    val bodyCall   = bodySchema
      .map(_ => """      .body(bodyJson)
                  |      .contentType("application/json")""".stripMargin)
      .getOrElse("")

    s"""  $scaladoc
       |  def $fnName(
       |      $paramList
       |  ): Request[Either[String, String]] = {
       |    basicRequest
       |      $verbCall
       |${headerLines.mkString("\n")}
       |$bodyCall
       |  }""".stripMargin.replaceAll("\n+\\s*\n", "\n")
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
      val sec = s.security
      if (sec.httpBearer.isDefined || sec.oAuth2InBearer.isDefined || sec.openIdConnectInBearer.isDefined)
        Seq(s"${scalaSafeIdent(s.name)}Token: String")
      else if (sec.httpBasic.isDefined)
        Seq(s"${scalaSafeIdent(s.name)}Username: String", s"${scalaSafeIdent(s.name)}Password: String")
      else if (sec.apiKeyInHeader.isDefined || sec.apiKeyInQuery.isDefined || sec.apiKeyInCookie.isDefined)
        Seq(s"${scalaSafeIdent(s.name)}Value: String")
      else Seq.empty
    }

  private def securityHeaderLines(schemes: Seq[BaklavaSecuritySchemaSerializable]): Seq[String] =
    schemes.headOption.toSeq.flatMap { s =>
      val sec  = s.security
      val name = scalaSafeIdent(s.name)
      if (sec.httpBearer.isDefined || sec.oAuth2InBearer.isDefined || sec.openIdConnectInBearer.isDefined)
        Seq(s"""      .header("Authorization", s"Bearer $${${name}Token}")""")
      else if (sec.httpBasic.isDefined)
        Seq(s"""      .auth.basic(${name}Username, ${name}Password)""")
      else if (sec.apiKeyInHeader.isDefined) {
        val k = sec.apiKeyInHeader.get.name
        Seq(s"""      .header("$k", ${name}Value)""")
      } else if (sec.apiKeyInCookie.isDefined) {
        val k = sec.apiKeyInCookie.get.name
        Seq(s"""      .cookie("$k", ${name}Value)""")
      } else Seq.empty
    }

  private def securityQueryLines(schemes: Seq[BaklavaSecuritySchemaSerializable]): Seq[String] =
    schemes.headOption.toSeq.flatMap { s =>
      val sec  = s.security
      val name = scalaSafeIdent(s.name)
      sec.apiKeyInQuery.toSeq.map(k => s"""        .addParam("${k.name}", ${name}Value)""")
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

  def renderReadme: String =
    s"""# Baklava-generated sttp-client
       |
       |This directory contains a Scala source tree emitted from your Baklava test cases. It uses
       |[sttp-client4](https://sttp.softwaremill.com) and is framework-agnostic — generated functions
       |return `Request[Either[String, String]]` values that you `.send(backend)` with any sttp backend
       |(sync, async, fs2, Future, etc.).
       |
       |## Layout
       |
       |- `src/main/scala/$basePackage/common/Types.scala` — case classes shared by 2+ tags (omitted if empty)
       |- `src/main/scala/$basePackage/{tag}/Types.scala` — tag-local case classes (omitted if empty)
       |- `src/main/scala/$basePackage/{tag}/Endpoints.scala` — one `{Tag}Endpoints` object with a
       |  `def` per endpoint
       |
       |## Usage
       |
       |Copy the files into your project under a matching package, add
       |`"com.softwaremill.sttp.client4" %% "core" % "4.x.y"` to your dependencies, then:
       |
       |```scala
       |import sttp.client4._
       |import sttp.model.Uri
       |import $basePackage.users.UsersEndpoints
       |
       |val backend = DefaultSyncBackend()
       |val base    = uri"https://api.example.com"
       |
       |val req = UsersEndpoints.listUsers(bearerAuthToken = "jwt", baseUri = base)
       |val res = req.send(backend)
       |```
       |
       |Request bodies take a pre-serialized JSON string (`bodyJson: String`) — bring your own JSON
       |codec (circe, jsoniter, upickle, etc.) to produce it.
       |""".stripMargin
}

private[sttpclient] object BaklavaSttpClientGenerator {
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
