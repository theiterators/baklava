package pl.iterators.baklava.orpc

import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.{TsNaming, TsZodDialect, TsZodRenderer}
import sttp.model.Method

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

// Endpoints nest by path segment (oRPC's native router shape): `/v1/auctions/{auctionId}/bids`
// becomes `contracts.v1.auctions.byAuctionId.bids.<method>`. Path parameters read as
// `by<Param>` — the router-tree spelling of tsfetch's `getUsersByUserId` function names.
private[orpc] object OrpcRouter {
  type Endpoint = ((Option[Method], String), Seq[BaklavaSerializableCall])

  final case class RouterChild(rawSegment: String, node: RouterNode)
  final case class RouterNode(
      procedures: Map[String, Endpoint],
      children: Map[String, RouterChild]
  )
  object RouterNode {
    val empty: RouterNode = RouterNode(Map.empty, Map.empty)
  }

  /** One generated source file: a subtree mounted at `contractsKeyPath` inside `contracts.ts`. `spread = true` marks a subtree holding
    * only the procedures declared directly at its mount point (e.g. `GET /v1` or `GET /`), merged in via object spread.
    */
  final case class RouterModule(
      constName: String,
      filePath: String,
      contractsKeyPath: List[String],
      spread: Boolean,
      node: RouterNode
  )
}

class BaklavaDslFormatterOrpc extends BaklavaDslFormatter {
  import OrpcRouter.*

  private val plainRenderer = new TsZodRenderer(TsZodDialect.orpc)

  private val dirName                 = "target/baklava/orpc"
  private val sourcesDirName          = "target/baklava/orpc/src"
  private val packageContractJsonPath = s"$dirName/package-contracts.json"

  private val contractTsPath = s"$sourcesDirName/contracts.ts"
  private val schemasTsPath  = s"$sourcesDirName/schemas.ts"
  private val clientTsPath   = s"$sourcesDirName/client.ts"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    // Create all target directories upfront so downstream writes don't depend on ordering.
    new File(dirName).mkdirs()
    new File(sourcesDirName).mkdirs()

    BaklavaOrpcFiles.files.foreach { case (file, content) =>
      writeTo(s"$dirName/$file", content)
    }

    config
      .get("orpc-package-contract-json")
      .foreach(packageContractJson => writeTo(packageContractJsonPath, packageContractJson))

    val errorCodeField = config.getOrElse("orpc-error-code-field", "type")

    // Sorted so tree insertion (and thus collision-suffix assignment) is deterministic.
    val endpoints: Seq[Endpoint] = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .sortBy { case ((method, path), _) => (path, method.map(_.toString).getOrElse("")) }

    val refs = buildSchemaRefs(endpoints, errorCodeField)
    if (refs.nonEmpty) writeSchemasFile(refs)

    val modules = modulesOf(buildRouterTree(endpoints))
    modules.foreach(writeModuleFile(_, errorCodeField, refs))
    writeContractsFile(modules)

    writeTo(clientTsPath, BaklavaOrpcFiles.clientTs(errorCodeField))
  }

  private def hash4(s: String): String = f"${s.hashCode.abs}%x".take(4)

  private def insert(node: RouterNode, segments: List[String], methodKey: String, endpoint: Endpoint): RouterNode =
    segments match {
      case Nil =>
        // Distinct symbolic paths can collapse to one key path (`/users/{id}` vs `/users/by-id`);
        // the later (sorted) endpoint keeps a suffixed method key instead of silently overwriting.
        val key = if (node.procedures.contains(methodKey)) methodKey + hash4(endpoint._1._2) else methodKey
        node.copy(procedures = node.procedures.updated(key, endpoint))
      case segment :: rest =>
        val base = TsNaming.segmentKey(segment)
        val key  = node.children.get(base) match {
          case Some(child) if child.rawSegment != segment => base + hash4(segment)
          case _                                          => base
        }
        val childNode = node.children.get(key).map(_.node).getOrElse(RouterNode.empty)
        node.copy(children = node.children.updated(key, RouterChild(segment, insert(childNode, rest, methodKey, endpoint))))
    }

  private[orpc] def buildRouterTree(endpoints: Seq[Endpoint]): RouterNode =
    endpoints.foldLeft(RouterNode.empty) { case (tree, endpoint @ ((method, path), _)) =>
      val segments  = path.split("/").toList.filter(_.nonEmpty)
      val methodKey = method.map(_.method.toLowerCase).getOrElse("any")
      insert(tree, segments, methodKey, endpoint)
    }

  private def versionLike(segment: String): Boolean = segment.matches("v[0-9]+")

  private def constNameOf(name: String): String = {
    val cleaned = name.filter(c => c.isLetterOrDigit || c == '_' || c == '$')
    if (cleaned.isEmpty || cleaned.head.isDigit) "_" + cleaned else cleaned
  }

  // A version prefix (`/v1/...`) is organizational, not a resource: modules live one level below
  // it (file per `/v1/<area>`), while non-versioned APIs get a file per top-level area.
  private[orpc] def modulesOf(tree: RouterNode): Seq[RouterModule] = {
    val rootModule =
      if (tree.procedures.isEmpty) Seq.empty
      else Seq(RouterModule("root", "root.contract.ts", Nil, spread = true, tree.copy(children = Map.empty)))

    val areaModules = tree.children.toSeq.sortBy(_._1).flatMap { case (key, child) =>
      if (versionLike(child.rawSegment) && child.node.children.nonEmpty) {
        val versionRoot =
          if (child.node.procedures.isEmpty) Seq.empty
          else
            Seq(
              RouterModule(
                constNameOf(key + "Root"),
                s"$key/index.contract.ts",
                List(key),
                spread = true,
                child.node.copy(children = Map.empty)
              )
            )
        val subModules = child.node.children.toSeq.sortBy(_._1).map { case (subKey, subChild) =>
          RouterModule(
            constNameOf(key + TsNaming.capitalize(subKey)),
            s"$key/$subKey.contract.ts",
            List(key, subKey),
            spread = false,
            subChild.node
          )
        }
        versionRoot ++ subModules
      } else {
        Seq(RouterModule(constNameOf(key), s"$key.contract.ts", List(key), spread = false, child.node))
      }
    }
    rootModule ++ areaModules
  }

  private def writeTo(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))

  private[orpc] def buildParamsZod[P](
      paramsPerCall: Seq[Seq[P]],
      nameOf: P => String,
      schemaOf: P => BaklavaSchemaSerializable,
      renderer: TsZodRenderer
  ): Option[String] = renderer.buildParamsZod(paramsPerCall, nameOf, schemaOf)

  // A parameter group where every field is optional may be omitted by the caller entirely.
  private[orpc] def isFullyOptionalGroup[P](
      paramsPerCall: Seq[Seq[P]],
      schemaOf: P => BaklavaSchemaSerializable
  ): Boolean =
    paramsPerCall.distinct.forall(_.forall(p => !schemaOf(p).required))

  // --- Named, deduplicated schemas -----------------------------------------------------------

  private def collectObjectNodes(schema: BaklavaSchemaSerializable): Seq[BaklavaSchemaSerializable] = {
    val children =
      schema.properties.values.toSeq ++ schema.items.toSeq ++ schema.additionalPropertiesSchema.toSeq
    val self =
      if (schema.`type` == SchemaType.ObjectType && schema.properties.nonEmpty) Seq(schema) else Seq.empty
    self ++ children.flatMap(collectObjectNodes)
  }

  private val genericClassNames = Set("Object", "Map", "Option", "Some", "None", "List", "Seq", "Vector", "Set")

  private def hoistableName(schema: BaklavaSchemaSerializable): Option[String] =
    Option(schema.className)
      .filter(_.matches("[A-Za-z][A-Za-z0-9]*"))
      .filterNot(genericClassNames.contains)
      .map(n => n.head.toLower.toString + n.tail + "Schema")

  // Object schemas that occur more than once anywhere in the rendered output (bodies, success
  // outputs, declared error data — including nested occurrences) are hoisted into schemas.ts
  // under a name derived from the captured case-class name. Same derived name with a different
  // structure gets a deterministic hash suffix.
  private[orpc] def buildSchemaRefs(
      endpoints: Seq[((Option[Method], String), Seq[BaklavaSerializableCall])],
      errorCodeField: String
  ): Map[BaklavaSchemaSerializable, String] = {
    val calls    = endpoints.flatMap(_._2)
    val rendered =
      calls.flatMap(_.request.bodySchema).filterNot(plainRenderer.isEmptyBodyInstance) ++
        calls.filter(c => c.response.status.code >= 200 && c.response.status.code < 300).flatMap(_.response.bodySchema) ++
        endpoints.flatMap { case (_, epCalls) => errorDataSchemas(epCalls, errorCodeField).map(_._2).flatten }
    val counts    = rendered.flatMap(collectObjectNodes).groupBy(identity).view.mapValues(_.size).toMap
    val hoistable = counts.collect { case (schema, n) if n >= 2 => schema }.toSeq
    val named     = hoistable.flatMap(s => hoistableName(s).map(_ -> s))
    named
      .groupBy(_._1)
      .toSeq
      .flatMap { case (name, entries) =>
        val schemas = entries.map(_._2).sortBy(plainRenderer.zodDefinition)
        schemas.zipWithIndex.map { case (schema, i) =>
          val finalName = if (i == 0) name else s"$name${f"${plainRenderer.zodDefinition(schema).hashCode.abs}%x".take(4)}"
          schema -> finalName
        }
      }
      .toMap
  }

  private def writeSchemasFile(refs: Map[BaklavaSchemaSerializable, String]): Unit = {
    // Definition order: dependencies before dependents (a hoisted schema may reference another).
    val remaining = scala.collection.mutable.LinkedHashMap.from(refs.toSeq.sortBy(_._2))
    val ordered   = scala.collection.mutable.ListBuffer.empty[(BaklavaSchemaSerializable, String)]
    while (remaining.nonEmpty) {
      val ready = remaining.filter { case (schema, _) =>
        collectObjectNodes(schema).filterNot(_ == schema).forall(n => !remaining.contains(n))
      }
      ready.foreach { entry =>
        ordered += entry
        remaining -= entry._1
      }
    }
    val renderer = rendererWith(refs, _ => ())
    val defs     = ordered
      .map { case (schema, name) =>
        val body = renderer.zodDefinition(schema)
        s"export const $name = $body;"
      }
      .mkString("\n\n")
    writeTo(schemasTsPath, "import { z } from \"zod\";\n\n" + defs + "\n")
  }

  private def rendererWith(
      refs: Map[BaklavaSchemaSerializable, String],
      record: String => Unit
  ): TsZodRenderer =
    new TsZodRenderer(
      TsZodDialect.orpc,
      schema =>
        refs.get(schema).map { name =>
          record(name)
          name
        }
    )

  // --- Contract emission ---------------------------------------------------------------------

  private def reindent(block: String, depth: Int): String =
    if (depth == 0) block
    else {
      val pad = "  " * depth
      block.linesIterator.map(line => if (line.isEmpty) line else pad + line).mkString("\n")
    }

  private def renderNode(node: RouterNode, depth: Int, errorCodeField: String, renderer: TsZodRenderer): String = {
    val procedureEntries = node.procedures.toSeq.sortBy(_._1).map { case (methodKey, endpoint) =>
      reindent(createContractForEndpoint(endpoint, errorCodeField, renderer, keyOverride = Some(methodKey)), depth)
    }
    val procedureKeys = node.procedures.keySet
    val childEntries  = node.children.toSeq.sortBy(_._1).map { case (baseKey, child) =>
      // A static segment named like an HTTP method used at the same node (`GET /api` + `/api/get/...`)
      // would duplicate the object key; the child yields.
      val key = if (procedureKeys.contains(baseKey)) baseKey + hash4(child.rawSegment) else baseKey
      val pad = "  " * (depth + 1)
      s"$pad${plainRenderer.tsObjectKey(key)}: {\n${renderNode(child.node, depth + 1, errorCodeField, renderer)}\n$pad}"
    }
    (procedureEntries ++ childEntries).mkString(",\n")
  }

  private def writeModuleFile(module: RouterModule, errorCodeField: String, refs: Map[BaklavaSchemaSerializable, String]): Unit = {
    val usedRefs = scala.collection.mutable.SortedSet.empty[String]
    val renderer = rendererWith(refs, usedRefs += _)
    val code     =
      s"""export const ${module.constName} = {
         |${renderNode(module.node, 0, errorCodeField, renderer)}
         |};
         |""".stripMargin

    val schemasFrom  = if (module.filePath.contains('/')) "../schemas" else "./schemas"
    val schemaImport =
      if (usedRefs.isEmpty) ""
      else s"import { ${usedRefs.mkString(", ")} } from \"$schemasFrom\";\n"
    // A contract with no schemas at all (e.g. a bare WebSocket upgrade route) uses no `z` —
    // strict consumer tsconfigs (noUnusedLocals) reject the unused import.
    val zImport = if (code.contains("z.")) "import { z } from \"zod\";\n" else ""
    val path    = s"$sourcesDirName/${module.filePath}"
    new File(path).getParentFile.mkdirs()
    writeTo(path, zImport + "import { oc } from \"@orpc/contract\";\n" + schemaImport + "\n" + code)
  }

  private def writeContractsFile(modules: Seq[RouterModule]): Unit = {
    val imports = modules
      .map(m => s"""import { ${m.constName} } from "./${m.filePath.stripSuffix(".ts")}";""")
      .mkString("\n")

    val (rootModules, mounted) = modules.partition(_.contractsKeyPath.isEmpty)
    val mountedByTop           =
      mounted.map(_.contractsKeyPath.head).distinct.map(top => top -> mounted.filter(_.contractsKeyPath.head == top))

    val entries = rootModules.map(m => s"  ...${m.constName}") ++
      mountedByTop.map {
        case (top, Seq(single)) if single.contractsKeyPath.sizeIs == 1 =>
          if (single.constName == top) s"  $top"
          else s"  ${plainRenderer.tsObjectKey(top)}: ${single.constName}"
        case (top, group) =>
          val inner = group
            .map { m =>
              if (m.spread) s"    ...${m.constName}"
              else s"    ${plainRenderer.tsObjectKey(m.contractsKeyPath.last)}: ${m.constName}"
            }
            .mkString(",\n")
          s"  ${plainRenderer.tsObjectKey(top)}: {\n$inner\n  }"
      }

    writeTo(
      contractTsPath,
      s"""$imports
         |
         |export const contracts = {
         |${entries.mkString(",\n")}
         |};
         |""".stripMargin
    )
  }

  private def successStatuses(calls: Seq[BaklavaSerializableCall]): Seq[Int] =
    calls.map(_.response.status.code).filter(c => c >= 200 && c < 300).distinct.sorted

  // Contract endpoint generator: one `<method>: oc.route({...}).input(...).output(...).errors({...})` entry.
  private[orpc] def createContractForEndpoint(
      endpoint: ((Option[Method], String), Seq[BaklavaSerializableCall]),
      errorCodeField: String = "type",
      renderer: TsZodRenderer = plainRenderer,
      keyOverride: Option[String] = None
  ): String = {
    val ((httpMethodOpt, _), calls) = endpoint
    require(
      calls.nonEmpty,
      s"createContractForEndpoint called with empty calls for method=${httpMethodOpt.map(_.method)}"
    )
    val httpMethod = httpMethodOpt.map(_.method).getOrElse("ANY").toLowerCase
    val entryKey   = keyOverride.getOrElse(httpMethod)

    val firstCall   = calls.head
    val req         = firstCall.request
    val summary     = renderer.escapeTsSingleQuoted(calls.flatMap(_.request.operationSummary).distinct.mkString(" / "))
    val description = renderer.escapeTsSingleQuoted(calls.flatMap(_.request.operationDescription).distinct.mkString("\n\n"))

    val pathParamsZodOpt = buildParamsZod(
      calls.map(_.request.pathParametersSeq),
      (p: BaklavaPathParamSerializable) => p.name,
      (p: BaklavaPathParamSerializable) => p.schema,
      renderer
    )
    val queryParamsZodOpt = buildParamsZod(
      calls.map(_.request.queryParametersSeq),
      (p: BaklavaQueryParamSerializable) => p.name,
      (p: BaklavaQueryParamSerializable) => p.schema,
      renderer
    )
    val queryOptionalSuffix =
      if (isFullyOptionalGroup(calls.map(_.request.queryParametersSeq), (p: BaklavaQueryParamSerializable) => p.schema)) ".optional()"
      else ""
    val headersZodOpt = buildParamsZod(
      calls.map(_.request.headersSeq),
      (h: BaklavaHeaderSerializable) => h.name,
      (h: BaklavaHeaderSerializable) => h.schema,
      renderer
    )
    val headersOptionalSuffix =
      if (isFullyOptionalGroup(calls.map(_.request.headersSeq), (h: BaklavaHeaderSerializable) => h.schema)) ".optional()"
      else ""

    // --- Body --- (same variant-ordering rationale as the ts-rest formatter: broader multipart
    // shapes must precede narrower ones inside a z.union, or Zod strips fields silently.)
    val multipartPartSets = calls
      .flatMap(_.request.multipartFormData)
      .distinct
      .sortBy { parts =>
        val names = parts.map(_.name).distinct
        (-names.size, names.sorted.mkString(","))
      }
    val bodyZodOpt =
      if (multipartPartSets.nonEmpty) Some(renderer.collapseZodUnion(multipartPartSets.map(renderer.renderMultipartBody)))
      else {
        val bodySchemas    = calls.flatMap(_.request.bodySchema).distinct
        val notEmptyBodies = bodySchemas.filterNot(renderer.isEmptyBodyInstance)
        if (notEmptyBodies.isEmpty) None else Some(renderer.collapseZodUnion(notEmptyBodies.map(renderer.zod)))
      }

    // --- Success responses ---
    // One captured 2xx status: compact output (the plain body), `successStatus` on the route.
    // Several distinct 2xx statuses: `outputStructure: 'detailed'` with a union of
    // `{ status: z.literal(s), body }` objects — which-status is real information the API
    // returns, and a compact body union would destroy it.
    val succStatuses = successStatuses(calls)
    val successCalls = calls.filter(c => c.response.status.code >= 200 && c.response.status.code < 300)

    val (successStatusOpt, outputStructure, outputZodOpt) =
      if (succStatuses.size <= 1) {
        val outputZods = successCalls
          .map(_.response.bodySchema)
          .distinct
          .map {
            case Some(schema) => renderer.zod(schema)
            case None         => "z.void()"
          }
          .distinct
        (succStatuses.headOption, "detailed-input-only", if (successCalls.isEmpty) None else Some(renderer.collapseZodUnion(outputZods)))
      } else {
        val variants = succStatuses.map { status =>
          val schemas = successCalls.filter(_.response.status.code == status).flatMap(_.response.bodySchema).distinct
          if (schemas.isEmpty) s"z.object({status: z.literal($status)})"
          else s"z.object({status: z.literal($status), body: ${renderer.collapseZodUnion(schemas.map(renderer.zod))}})"
        }
        (None, "detailed", Some(s"z.union([${variants.mkString(", ")}])"))
      }

    val tags         = calls.flatMap(_.request.operationTags).distinct.sorted
    val tagsFieldOpt =
      if (tags.isEmpty) None
      else Some(s"      tags: [${tags.map(t => s"'${renderer.escapeTsSingleQuoted(t)}'").mkString(", ")}]")
    val operationIdOpt = calls
      .flatMap(_.request.operationId)
      .distinct
      .headOption
      .map(id => s"      operationId: '${renderer.escapeTsSingleQuoted(id)}'")

    val inputGroups = List(
      pathParamsZodOpt.map(z => s"      params: $z"),
      queryParamsZodOpt.map(z => s"      query: $z$queryOptionalSuffix"),
      headersZodOpt.map(z => s"      headers: $z$headersOptionalSuffix"),
      bodyZodOpt.map(z => s"      body: $z")
    ).flatten

    val routeFields = List(
      Some(s"      method: '${httpMethod.toUpperCase()}'"),
      Some(s"      path: '${req.symbolicPath}'"),
      Some(s"      summary: '$summary'"),
      Some(s"      description: '$description'"),
      operationIdOpt,
      tagsFieldOpt,
      successStatusOpt.map(s => s"      successStatus: $s"),
      Some(s"      inputStructure: 'detailed'"),
      if (outputStructure == "detailed") Some(s"      outputStructure: 'detailed'") else None
    ).flatten

    val lines = List(
      s"  $entryKey: oc",
      s"    .route({",
      routeFields.mkString(",\n"),
      s"    })"
    ) ++
      (if (inputGroups.isEmpty) Nil
       else
         List(
           s"    .input(z.object({",
           inputGroups.mkString(",\n"),
           s"    }))"
         )) ++
      outputZodOpt.toList.map(z => s"    .output($z)") ++
      declaredErrors(calls, errorCodeField, renderer).toList

    lines.mkString("\n")
  }

  private val errorCodeRegexCache = scala.collection.concurrent.TrieMap.empty[String, scala.util.matching.Regex]

  // Extract the discriminator value (e.g. RFC 9457 `type`) from a captured error body. The
  // captured example, not the schema, carries the literal — schemas only know it's a string.
  // Top-level string fields only; nested discriminators aren't supported.
  private[orpc] def extractErrorCode(bodyString: String, field: String): Option[String] = {
    val regex = errorCodeRegexCache.getOrElseUpdate(
      field,
      ("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").r
    )
    regex.findFirstMatchIn(bodyString).map(_.group(1))
  }

  // The discriminator property in a declared error's data schema is narrowed to the literal
  // code it was declared under, so payloads are self-discriminating (`switch` on it gets
  // exhaustiveness checking, not just narrowing by error code).
  private def withLiteralDiscriminator(
      schema: BaklavaSchemaSerializable,
      field: String,
      code: String
  ): BaklavaSchemaSerializable =
    schema.properties.get(field) match {
      case Some(prop) if prop.`type` == SchemaType.StringType && prop.`enum`.isEmpty =>
        schema.copy(properties = schema.properties.updated(field, prop.copy(format = None, `enum` = Some(Set(code)))))
      case _ => schema
    }

  private def errorDataSchemas(
      calls: Seq[BaklavaSerializableCall],
      errorCodeField: String
  ): Seq[(String, Seq[BaklavaSchemaSerializable])] = {
    val errorCalls = calls.filter(c => c.response.status.code < 200 || c.response.status.code >= 300)
    errorCalls
      .flatMap(c => extractErrorCode(c.response.bodyString, errorCodeField).map(_ -> c))
      .groupBy(_._1)
      .toList
      .sortBy(_._1)
      .map { case (code, codeCalls) =>
        code -> codeCalls.flatMap(_._2.response.bodySchema).distinct.map(withLiteralDiscriminator(_, errorCodeField, code))
      }
  }

  // Declared, typed errors: non-2xx calls grouped by the code extracted from their example
  // bodies. Codes match what a client-side error decoder should set on its ORPCErrors, making
  // `isDefinedError` narrowing work end to end. Calls whose body carries no extractable code
  // (bodyless 429s, non-JSON payloads) are left undeclared and surface via oRPC's defaults.
  private[orpc] def declaredErrors(
      calls: Seq[BaklavaSerializableCall],
      errorCodeField: String,
      renderer: TsZodRenderer = plainRenderer
  ): Option[String] = {
    val errorCalls = calls.filter(c => c.response.status.code < 200 || c.response.status.code >= 300)
    val byCode     = errorDataSchemas(calls, errorCodeField)
    if (byCode.isEmpty) None
    else {
      val statusByCode = errorCalls
        .flatMap(c => extractErrorCode(c.response.bodyString, errorCodeField).map(_ -> c.response.status.code))
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2).min)
        .toMap
      val entries = byCode.map { case (code, schemas) =>
        val status  = statusByCode(code)
        val dataOpt = if (schemas.isEmpty) None else Some(s"        data: ${renderer.collapseZodUnion(schemas.map(renderer.zod))}")
        val fields  = List(Some(s"        status: $status"), dataOpt).flatten.mkString(",\n")
        s"      '${renderer.escapeTsSingleQuoted(code)}': {\n$fields\n      }"
      }
      Some(s"    .errors({\n${entries.mkString(",\n")}\n    })")
    }
  }

}
