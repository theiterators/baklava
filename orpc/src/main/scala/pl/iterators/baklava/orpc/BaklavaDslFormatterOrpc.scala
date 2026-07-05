package pl.iterators.baklava.orpc

import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.{TsPathRouter, TsZodDialect, TsZodRenderer}
import sttp.model.Method

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

class BaklavaDslFormatterOrpc extends BaklavaDslFormatter {
  import TsPathRouter.*

  private val plainRenderer = new TsZodRenderer(TsZodDialect.orpc)

  private val dirName                 = "target/baklava/orpc"
  private val sourcesDirName          = "target/baklava/orpc/src"
  private val packageContractJsonPath = s"$dirName/package-contracts.json"

  private val contractTsPath = s"$sourcesDirName/contracts.ts"
  private val schemasTsPath  = s"$sourcesDirName/schemas.ts"
  private val clientTsPath   = s"$sourcesDirName/client.ts"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    // Module files are named after the current route set; without a wipe, files from a previous
    // run (renamed or removed routes) would linger and ship to consumers syncing the directory.
    deleteRecursively(new File(sourcesDirName))
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

  private def writeTo(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))

  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) Option(file.listFiles()).toSeq.flatten.foreach(deleteRecursively)
    val _ = file.delete()
  }

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

  private def writeModuleFile(module: RouterModule, errorCodeField: String, refs: Map[BaklavaSchemaSerializable, String]): Unit = {
    val usedRefs = scala.collection.mutable.SortedSet.empty[String]
    val renderer = rendererWith(refs, usedRefs += _)
    val body     = TsPathRouter.render(
      module.node,
      0,
      plainRenderer.tsObjectKey,
      (endpoint, key) => createContractForEndpoint(endpoint, errorCodeField, renderer, keyOverride = Some(key))
    )
    val code =
      s"""export const ${module.constName} = {
         |$body
         |};
         |""".stripMargin

    val filePath     = moduleFilePath(module)
    val schemasFrom  = if (filePath.contains('/')) "../schemas" else "./schemas"
    val schemaImport =
      if (usedRefs.isEmpty) ""
      else s"import { ${usedRefs.mkString(", ")} } from \"$schemasFrom\";\n"
    // A contract with no schemas at all (e.g. a bare WebSocket upgrade route) uses no `z` —
    // strict consumer tsconfigs (noUnusedLocals) reject the unused import.
    val zImport = if (code.contains("z.")) "import { z } from \"zod\";\n" else ""
    val path    = s"$sourcesDirName/$filePath"
    new File(path).getParentFile.mkdirs()
    writeTo(path, zImport + "import { oc } from \"@orpc/contract\";\n" + schemaImport + "\n" + code)
  }

  private def moduleFilePath(module: RouterModule): String =
    module.fileSegments.mkString("/") + ".contract.ts"

  private def writeContractsFile(modules: Seq[RouterModule]): Unit = {
    val imports = modules
      .map(m => s"""import { ${m.constName} } from "./${moduleFilePath(m).stripSuffix(".ts")}";""")
      .mkString("\n")

    val (rootModules, mounted) = modules.partition(_.mountPath.isEmpty)
    val mountedByTop           =
      mounted.map(_.mountPath.head).distinct.map(top => top -> mounted.filter(_.mountPath.head == top))

    val entries = rootModules.map(m => s"  ...${m.constName}") ++
      mountedByTop.map {
        case (top, Seq(single)) if single.mountPath.sizeIs == 1 =>
          if (single.constName == top) s"  $top"
          else s"  ${plainRenderer.tsObjectKey(top)}: ${single.constName}"
        case (top, group) =>
          val inner = group
            .map { m =>
              if (m.spread) s"    ...${m.constName}"
              else s"    ${plainRenderer.tsObjectKey(m.mountPath.last)}: ${m.constName}"
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
