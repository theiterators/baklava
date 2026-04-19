package pl.iterators.baklava.sttpclient

import pl.iterators.baklava.*

/** Translates captured `BaklavaSerializableCall`s to Scala source: one file per operation tag with sttp-client4 request builders, plus a
  * `Types.scala` with case classes for named object schemas. The generated code is framework-agnostic — it returns sttp `Request` values
  * that users `.send(backend)` with their own chosen sttp backend and JSON codec.
  */
private[sttpclient] class BaklavaSttpClientGenerator(packageName: String, calls: Seq[BaklavaSerializableCall]) {

  /** Deduped map of className → rendered case-class body (field list). */
  private val namedCaseClasses: Map[String, String] = {
    val collected                                      = scala.collection.mutable.LinkedHashMap.empty[String, String]
    def visit(schema: BaklavaSchemaSerializable): Unit = schema.`type` match {
      case SchemaType.ObjectType if isNamedCaseClass(schema) =>
        if (!collected.contains(schema.className))
          collected(schema.className) = renderCaseClassBody(schema)
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

  def renderTypes: String = {
    val body =
      if (namedCaseClasses.isEmpty) "// (no named case classes in this API)\n"
      else
        namedCaseClasses.toSeq
          .sortBy(_._1)
          .map { case (name, caseBody) => s"final case class ${scalaSafeIdent(name)}($caseBody)" }
          .mkString("\n\n") + "\n"

    s"""package $packageName
       |
       |$body""".stripMargin
  }

  /** One Scala file per operation tag (or `Default.scala` for untagged calls). Returns `(filename, content)` pairs. */
  def renderTagFiles: Seq[(String, String)] = {
    val byTag = calls
      .groupBy(c => c.request.operationTags.headOption.getOrElse("default"))
    byTag.toSeq.sortBy(_._1).map { case (tag, tagCalls) =>
      val objectName = scalaSafeIdent(capitalize(tag)) + "Endpoints"
      val endpoints  = tagCalls
        .groupBy(c => (c.request.method.map(_.method.toUpperCase).getOrElse("GET"), c.request.symbolicPath))
        .toSeq
        .sortBy { case ((m, p), _) => (p, m) }
        .map { case (_, endpointCalls) => renderEndpoint(endpointCalls) }

      val fileName = s"$objectName.scala"
      val content  =
        s"""package $packageName
           |
           |import sttp.client4.*
           |import sttp.model.Uri
           |
           |object $objectName {
           |
           |${endpoints.mkString("\n\n")}
           |}
           |""".stripMargin
      fileName -> content
    }
  }

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

    // Build the parameter list: path (required), query (required/optional), headers (required/optional), body (String, required).
    val pathParamDefs  = pathParams.map(p => s"${scalaSafeIdent(p.name)}: ${scalaType(p.schema)}")
    val queryParamDefs = queryParams.map(p => {
      val t = scalaType(p.schema)
      if (p.schema.required) s"${scalaSafeIdent(p.name)}: $t"
      else s"${scalaSafeIdent(p.name)}: Option[$t] = None"
    })
    val headerParamDefs = declaredHs.map(h => {
      val t = scalaType(h.schema)
      if (h.schema.required) s"${scalaSafeIdent(h.name)}: $t"
      else s"${scalaSafeIdent(h.name)}: Option[$t] = None"
    })
    val bodyParamDef = bodySchema.toSeq.map(_ => "bodyJson: String")
    val authParams   = securityCredentialParams(req.securitySchemes)

    val allParams = (pathParamDefs ++ queryParamDefs ++ headerParamDefs ++ bodyParamDef ++ authParams ++ Seq("baseUri: Uri"))
    val paramList = allParams.mkString(",\n      ")

    val pathExpr      = renderPathExpression(req.symbolicPath, pathParams.map(_.name))
    val queryAddLines = queryParams.map { p =>
      val name = p.name
      val id   = scalaSafeIdent(name)
      if (p.schema.required) s"""        .addParam("$name", $id.toString)"""
      else s"""        .addParam(Option.when($id.isDefined)("$name" -> $id.get.toString))"""
    }

    val headerLines = securityHeaderLines(req.securitySchemes) ++
      declaredHs.map { h =>
        val name = h.name
        val id   = scalaSafeIdent(h.name)
        if (h.schema.required) s"""      .header("$name", $id.toString)"""
        else s"""      .header(Option.when($id.isDefined)(Header("$name", $id.get.toString)))"""
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
       |  }""".stripMargin.replaceAll("\n+\\s*\n", "\n") // collapse blank-line holes when bodyCall is empty
  }

  private def renderPathExpression(symbolicPath: String, pathParamNames: Seq[String]): String = {
    val interpolated = pathParamNames.foldLeft(symbolicPath) { (acc, name) =>
      acc.replace(s"{$name}", s"$$${scalaSafeIdent(name)}")
    }
    s"""baseUri.addPath("${interpolated.stripPrefix("/").split("/").mkString("\", \"")}")"""
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
      else
        Seq.empty
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
      } else Seq.empty
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
      if (schema.`enum`.exists(_.nonEmpty)) "String" // users can refine to sealed enum manually
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

  /** Strip non-identifier characters and reserved words. Keeps deterministic output even when schema class names contain generic brackets
    * or the user supplied a weird tag.
    */
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
       |- `src/main/scala/$packageName/Types.scala` — case classes for named schemas
       |- `src/main/scala/$packageName/${"{Tag}"}Endpoints.scala` — one object per operation tag, with a
       |  `def` per endpoint
       |
       |## Usage
       |
       |Copy the files into your project under a matching package, add
       |`"com.softwaremill.sttp.client4" %% "core" % "4.x.y"` to your dependencies, then:
       |
       |```scala
       |import sttp.client4.*
       |import sttp.model.Uri
       |import $packageName.*
       |
       |val backend = DefaultSyncBackend()
       |val base    = uri"https://api.example.com"
       |
       |val req = UsersEndpoints.listUsers(baseUri = base)
       |val res = req.send(backend)
       |```
       |
       |Request bodies take a pre-serialized JSON string (`bodyJson: String`) — bring your own JSON
       |codec (circe, jsoniter, upickle, etc.) to produce it.
       |""".stripMargin
}

private[sttpclient] object BaklavaSttpClientGenerator {
  // Kept in the companion so it's eagerly available before the class body's val-initialization runs.
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
