package pl.iterators.baklava.tsrest

import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.{TsPathRouter, TsSchemaRefs, TsZodDialect, TsZodRenderer}
import sttp.model.Method

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

class BaklavaDslFormatterTsRest extends BaklavaDslFormatter {
  import TsPathRouter.*

  private val plainRenderer = new TsZodRenderer(TsZodDialect.tsRest)

  private val dirName                 = "target/baklava/tsrest"
  private val sourcesDirName          = "target/baklava/tsrest/src"
  private val packageContractJsonPath = s"$dirName/package-contracts.json"

  private val contractTsPath = s"$sourcesDirName/contracts.ts"
  private val schemasTsPath  = s"$sourcesDirName/schemas.ts"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    // Module files are named after the current route set; without a wipe, files from a previous
    // run (renamed or removed routes) would linger and ship to consumers syncing the directory.
    deleteRecursively(new File(sourcesDirName))
    // Create all target directories upfront so downstream writes don't depend on ordering.
    new File(dirName).mkdirs()
    new File(sourcesDirName).mkdirs()

    BaklavaTsRestFiles.files.foreach { case (file, content) =>
      writeTo(s"$dirName/$file", content)
    }

    config
      .get("ts-rest-package-contract-json")
      .foreach(packageContractJson => writeTo(packageContractJsonPath, packageContractJson))

    // Sorted so tree insertion (and thus collision-suffix assignment) is deterministic.
    val endpoints: Seq[Endpoint] = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .sortBy { case ((method, path), _) => (path, method.map(_.toString).getOrElse("")) }

    val refs = buildSchemaRefs(endpoints)
    if (refs.nonEmpty) writeTo(schemasTsPath, TsSchemaRefs.schemasFileContent(refs, rendererWith(refs, _ => ()).zodDefinition))

    val modules = modulesOf(buildRouterTree(endpoints))
    modules.foreach(writeModuleFile(_, refs))
    writeContractsFile(modules)
  }

  private[tsrest] def buildSchemaRefs(endpoints: Seq[Endpoint]): Map[BaklavaSchemaSerializable, String] = {
    val calls    = endpoints.flatMap(_._2)
    val rendered =
      calls.flatMap(_.request.bodySchema).filterNot(plainRenderer.isEmptyBodyInstance) ++
        calls.flatMap(_.response.bodySchema)
    TsSchemaRefs.buildRefs(rendered, plainRenderer.zodDefinition)
  }

  private def rendererWith(
      refs: Map[BaklavaSchemaSerializable, String],
      record: String => Unit
  ): TsZodRenderer =
    new TsZodRenderer(
      TsZodDialect.tsRest,
      schema =>
        refs.get(schema).map { name =>
          record(name)
          name
        }
    )

  private def moduleFilePath(module: RouterModule): String =
    module.fileSegments.mkString("/") + ".contract.ts"

  private def writeModuleFile(module: RouterModule, refs: Map[BaklavaSchemaSerializable, String]): Unit = {
    val usedRefs = scala.collection.mutable.SortedSet.empty[String]
    val renderer = rendererWith(refs, usedRefs += _)
    val body     = TsPathRouter.render(
      module.node,
      0,
      plainRenderer.tsObjectKey,
      (endpoint, key) => createContractForEndpoint(endpoint, keyOverride = Some(key), renderer = renderer)
    )
    val code =
      s"""export const ${module.constName} = initContract().router({
         |$body
         |});
         |""".stripMargin
    val filePath     = moduleFilePath(module)
    val schemasFrom  = if (filePath.contains('/')) "../schemas" else "./schemas"
    val schemaImport =
      if (usedRefs.isEmpty) ""
      else s"import { ${usedRefs.mkString(", ")} } from \"$schemasFrom\";\n"
    val path = s"$sourcesDirName/$filePath"
    new File(path).getParentFile.mkdirs()
    writeTo(
      path,
      """import { z } from "zod";
        |import { initContract } from "@ts-rest/core";
        |""".stripMargin + schemaImport + "\n" + code
    )
  }

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
      s"""import { initContract } from "@ts-rest/core";
         |$imports
         |
         |export const contracts = initContract().router({
         |${entries.mkString(",\n")}
         |});
         |""".stripMargin
    )
  }

  private def writeTo(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))

  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) Option(file.listFiles()).toSeq.flatten.foreach(deleteRecursively)
    val _ = file.delete()
  }

  private[tsrest] def buildParamsZod[P](
      paramsPerCall: Seq[Seq[P]],
      nameOf: P => String,
      schemaOf: P => BaklavaSchemaSerializable
  ): Option[String] = plainRenderer.buildParamsZod(paramsPerCall, nameOf, schemaOf)

  private[tsrest] def tsObjectKey(name: String): String = plainRenderer.tsObjectKey(name)

  // Render one captured `Multipart` value as a ts-rest body schema (see
  // https://ts-rest.com/docs/core/multi-part): a `z.object` keyed by part name, `FilePart` ->
  // `z.instanceof(File)`, `TextPart` -> `z.string()`. A repeated part name (a multi-value form
  // field) becomes a `z.array(...)`; a name that mixes file and text parts unions the element
  // schemas. Names and element schemas are sorted so output is deterministic.
  private[tsrest] def renderMultipartBody(parts: Seq[BaklavaMultipartPartSerializable]): String =
    plainRenderer.renderMultipartBody(parts)

  /** Convert a Baklava `{name}` placeholder path to the ts-rest `:name` syntax. Non-placeholder braces (i.e. anything containing `/` or
    * nested braces) are left alone. Param names can contain any character except `{`, `}`, or `/` — so hyphens and dots survive.
    */
  private[tsrest] def toTsRestPath(symbolicPath: String): String =
    symbolicPath.replaceAll("""\{([^{}/]+)\}""", ":$1")

  // Contract endpoint generator
  private[tsrest] def createContractForEndpoint(
      endpoint: ((Option[Method], String), Seq[BaklavaSerializableCall]),
      keyOverride: Option[String] = None,
      renderer: TsZodRenderer = plainRenderer
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
    val summary     = escapeTsSingleQuoted(calls.flatMap(_.request.operationSummary).distinct.mkString(" / "))
    val description = escapeTsSingleQuoted(calls.flatMap(_.request.operationDescription).distinct.mkString("\n\n"))
    val path        = toTsRestPath(req.symbolicPath)

    val pathParamsZodOpt = renderer.buildParamsZod(
      calls.map(_.request.pathParametersSeq),
      (p: BaklavaPathParamSerializable) => p.name,
      (p: BaklavaPathParamSerializable) => p.schema
    )
    val queryParamsZodOpt = renderer.buildParamsZod(
      calls.map(_.request.queryParametersSeq),
      (p: BaklavaQueryParamSerializable) => p.name,
      (p: BaklavaQueryParamSerializable) => p.schema
    )
    val headersZodOpt = renderer.buildParamsZod(
      calls.map(_.request.headersSeq),
      (h: BaklavaHeaderSerializable) => h.name,
      (h: BaklavaHeaderSerializable) => h.schema
    )
    // --- Body ---
    // A `multipart/form-data` body has a free-form schema, so it can't be projected through `zod`;
    // instead emit `contentType: 'multipart/form-data'` plus a `z.object` of the captured part
    // names. Each call's part-set is rendered separately and combined into a `z.union`, mirroring
    // how distinct non-multipart body shapes are handled. Variants are ordered by descending
    // field count (sorted part names as a stable tiebreaker, so `z.object({})` lands last) before
    // unioning: a shape with more required fields must be tried before a more-permissive one that
    // would also accept it, otherwise Zod's non-strict `z.object` matches the narrower branch first
    // and silently strips the extra fields.
    val multipartPartSets = calls
      .flatMap(_.request.multipartFormData)
      .distinct
      .sortBy { parts =>
        val names = parts.map(_.name).distinct
        (-names.size, names.sorted.mkString(","))
      }
    val (contentTypeLineOpt, bodyZod) =
      if (multipartPartSets.nonEmpty)
        (Some("    contentType: 'multipart/form-data',"), renderer.collapseZodUnion(multipartPartSets.map(renderer.renderMultipartBody)))
      else {
        val bodySchemas = calls.flatMap(_.request.bodySchema).distinct
        val bodyZods    =
          if (bodySchemas.isEmpty) Seq("z.undefined()")
          else if (bodySchemas.size == 1 && isEmptyBodyInstance(bodySchemas.head)) Seq("z.undefined()")
          else {
            val notEmptyBodies = bodySchemas.filterNot(isEmptyBodyInstance)
            if (notEmptyBodies.isEmpty) Seq("z.undefined()") else notEmptyBodies.map(renderer.zod)
          }
        (None, renderer.collapseZodUnion(bodyZods))
      }

    // --- Responses ---
    val responses = calls
      .groupBy(_.response.status.code)
      .toList
      .sortBy(_._1)
      .map { case (status, respCalls) =>
        val schemas = respCalls.flatMap(_.response.bodySchema).distinct.map(renderer.zod)
        val zodStr  = renderer.collapseZodUnion(schemas)
        s"      $status: $zodStr"
      }
      .mkString(",\n")

    val bodyLine =
      if (httpMethod.equals("get") && bodyZod == "z.undefined()") None
      else Some(s"    body: $bodyZod,")

    // Compose contract entry
    val lines = List(
      s"  $entryKey: {",
      s"    summary: '${summary}',",
      s"    description: '${description}',",
      s"    method: '${httpMethod.toUpperCase()}',",
      s"    path: '$path',"
    ).++(pathParamsZodOpt.toList.map(z => s"    pathParams: $z,"))
      .++(queryParamsZodOpt.toList.map(z => s"    query: $z,"))
      .++(headersZodOpt.toList.map(z => s"    headers: $z,"))
      // `contentType` only makes sense alongside `body`, so emit them as one block (or neither).
      .++(bodyLine.toList.flatMap(line => contentTypeLineOpt.toList :+ line))
      .++(
        List(
          s"    responses: {",
          s"$responses",
          s"    }",
          s"  }"
        )
      )
    lines.mkString("\n")
  }

  private def isEmptyBodyInstance(schema: BaklavaSchemaSerializable): Boolean =
    plainRenderer.isEmptyBodyInstance(schema)

  private def escapeTsSingleQuoted(s: String): String = plainRenderer.escapeTsSingleQuoted(s)

  private[tsrest] def zod(schema: BaklavaSchemaSerializable): String = plainRenderer.zod(schema)

  private[tsrest] def collapseZodUnion(zods: Seq[String]): String = plainRenderer.collapseZodUnion(zods)

}
