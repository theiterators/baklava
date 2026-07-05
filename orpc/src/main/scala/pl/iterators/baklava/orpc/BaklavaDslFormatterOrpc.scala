package pl.iterators.baklava.orpc

import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.{TsNaming, TsZodDialect, TsZodRenderer}
import sttp.model.Method

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

class BaklavaDslFormatterOrpc extends BaklavaDslFormatter {
  private val renderer = new TsZodRenderer(TsZodDialect.orpc)

  private val dirName                 = "target/baklava/orpc"
  private val sourcesDirName          = "target/baklava/orpc/src"
  private val packageContractJsonPath = s"$dirName/package-contracts.json"

  private val contractTsPath = s"$sourcesDirName/contracts.ts"

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

    val groupedByBaseName = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .groupBy(c => contractNameFromSymbolicPath(c._1._2))
      .toList
      .sortBy(_._1)

    // Disambiguate contract-name collisions: if two distinct symbolicPaths map to the same derived
    // name (e.g. "/a/b" and "/a-b" both collapse to "a-b"), split each into its own contract with
    // a short deterministic hash suffix. Non-colliding names pass through unchanged.
    val callsGroupedBySymbolicPathIntoContractName = groupedByBaseName.flatMap { case (baseName, endpoints) =>
      val distinctPaths = endpoints.map(_._1._2).distinct
      if (distinctPaths.size <= 1) Seq((baseName, endpoints))
      else {
        endpoints.groupBy(_._1._2).toList.sortBy(_._1).map { case (symbolicPath, eps) =>
          val suffix = f"${symbolicPath.hashCode.abs}%x".take(4)
          (s"$baseName-$suffix", eps)
        }
      }
    }

    val errorCodeField = config.getOrElse("orpc-error-code-field", "type")

    val contractNames = callsGroupedBySymbolicPathIntoContractName
      .map { case (name, endpoints) =>
        val constName = createContractForGroup(name, endpoints, errorCodeField)
        (name, constName)
      }

    val importStmts = contractNames
      .map { case (name, constName) =>
        s"""import { $constName } from "./$name.contract";"""
      }
      .mkString("\n")

    val contractsMap = contractNames
      .map { case (name, constName) => s"""  "$name": $constName""" }
      .mkString(",\n")

    val typeMap = contractNames
      .map { case (name, constName) => s"""  "$name": typeof $constName""" }
      .mkString(";\n")

    writeTo(
      contractTsPath,
      s"""$importStmts

         |export const contracts: {
         |$typeMap
         |} = {
         |$contractsMap
         |};
         |\n""".stripMargin
    )
  }

  private def writeTo(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))

  private[orpc] def buildParamsZod[P](
      paramsPerCall: Seq[Seq[P]],
      nameOf: P => String,
      schemaOf: P => BaklavaSchemaSerializable
  ): Option[String] = renderer.buildParamsZod(paramsPerCall, nameOf, schemaOf)

  // A parameter group where every field is optional may be omitted by the caller entirely.
  private[orpc] def isFullyOptionalGroup[P](
      paramsPerCall: Seq[Seq[P]],
      schemaOf: P => BaklavaSchemaSerializable
  ): Boolean =
    paramsPerCall.distinct.forall(_.forall(p => !schemaOf(p).required))

  private[orpc] def tsObjectKey(name: String): String = renderer.tsObjectKey(name)

  // Render one captured `Multipart` value as a body schema: a `z.object` keyed by part name,
  // `FilePart` -> `z.instanceof(File)`, `TextPart` -> `z.string()`. oRPC serializes a body
  // containing `File` values as `multipart/form-data` on the wire. A repeated part name
  // (a multi-value form field) becomes a `z.array(...)`; a name that mixes file and text parts
  // unions the element schemas. Names and element schemas are sorted so output is deterministic.
  private[orpc] def renderMultipartBody(parts: Seq[BaklavaMultipartPartSerializable]): String =
    renderer.renderMultipartBody(parts)

  private[orpc] def contractNameFromSymbolicPath(path: String): String =
    TsNaming.contractNameFromSymbolicPath(path)

  private def createContractForGroup(
      contractName: String,
      endpointsWithCalls: Seq[((Option[Method], String), Seq[BaklavaSerializableCall])],
      errorCodeField: String
  ): String = {
    val contractConstName = toCamelCase(contractName) + "Contract"
    val sortedEndpoints   = endpointsWithCalls.sortBy(_._1._1.map(_.toString).getOrElse(""))

    val code =
      s"""export const $contractConstName = {
         |${sortedEndpoints.map(e => createContractForEndpoint(e, errorCodeField)).mkString(",\n")}
         |};
         |""".stripMargin

    // A contract with no schemas at all (e.g. a bare WebSocket upgrade route) uses no `z` —
    // strict consumer tsconfigs (noUnusedLocals) reject the unused import.
    val zImport = if (code.contains("z.")) "import { z } from \"zod\";\n" else ""
    writeTo(
      s"$sourcesDirName/$contractName.contract.ts",
      zImport + "import { oc } from \"@orpc/contract\";\n\n" + code
    )
    contractConstName
  }

  private def successStatuses(calls: Seq[BaklavaSerializableCall]): Seq[Int] =
    calls.map(_.response.status.code).filter(c => c >= 200 && c < 300).distinct.sorted

  // Contract endpoint generator: one `<method>: oc.route({...}).input(...).output(...).errors({...})` entry.
  private[orpc] def createContractForEndpoint(
      endpoint: ((Option[Method], String), Seq[BaklavaSerializableCall]),
      errorCodeField: String = "type"
  ): String = {
    val ((httpMethodOpt, _), calls) = endpoint
    require(
      calls.nonEmpty,
      s"createContractForEndpoint called with empty calls for method=${httpMethodOpt.map(_.method)}"
    )
    val httpMethod = httpMethodOpt.map(_.method).getOrElse("ANY").toLowerCase

    val firstCall   = calls.head
    val req         = firstCall.request
    val summary     = escapeTsSingleQuoted(calls.flatMap(_.request.operationSummary).distinct.mkString(" / "))
    val description = escapeTsSingleQuoted(calls.flatMap(_.request.operationDescription).distinct.mkString("\n\n"))

    val pathParamsZodOpt = buildParamsZod(
      calls.map(_.request.pathParametersSeq),
      (p: BaklavaPathParamSerializable) => p.name,
      (p: BaklavaPathParamSerializable) => p.schema
    )
    val queryParamsZodOpt = buildParamsZod(
      calls.map(_.request.queryParametersSeq),
      (p: BaklavaQueryParamSerializable) => p.name,
      (p: BaklavaQueryParamSerializable) => p.schema
    )
    val queryOptionalSuffix =
      if (isFullyOptionalGroup(calls.map(_.request.queryParametersSeq), (p: BaklavaQueryParamSerializable) => p.schema)) ".optional()"
      else ""
    val headersZodOpt = buildParamsZod(
      calls.map(_.request.headersSeq),
      (h: BaklavaHeaderSerializable) => h.name,
      (h: BaklavaHeaderSerializable) => h.schema
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
      if (multipartPartSets.nonEmpty) Some(collapseZodUnion(multipartPartSets.map(renderMultipartBody)))
      else {
        val bodySchemas    = calls.flatMap(_.request.bodySchema).distinct
        val notEmptyBodies = bodySchemas.filterNot(isEmptyBodyInstance)
        if (notEmptyBodies.isEmpty) None else Some(collapseZodUnion(notEmptyBodies.map(zod)))
      }

    // --- Success responses ---
    // oRPC models one success shape per procedure: the lowest captured 2xx becomes
    // `successStatus`, all captured 2xx bodies union into `.output(...)`. Bodyless
    // success (204 etc.) renders as `z.void()`.
    val succStatuses     = successStatuses(calls)
    val successStatusOpt = succStatuses.headOption
    val successCalls     = calls.filter(c => c.response.status.code >= 200 && c.response.status.code < 300)
    val successSchemas   = successCalls.map(_.response.bodySchema).distinct
    val outputZods       = successSchemas.map {
      case Some(schema) => zod(schema)
      case None         => "z.void()"
    }.distinct
    val outputZodOpt = if (successCalls.isEmpty) None else Some(collapseZodUnion(outputZods))

    val inputGroups = List(
      pathParamsZodOpt.map(z => s"      params: $z"),
      queryParamsZodOpt.map(z => s"      query: $z$queryOptionalSuffix"),
      headersZodOpt.map(z => s"      headers: $z$headersOptionalSuffix"),
      bodyZodOpt.map(z => s"      body: $z")
    ).flatten

    val tags         = calls.flatMap(_.request.operationTags).distinct.sorted
    val tagsFieldOpt =
      if (tags.isEmpty) None
      else Some(s"      tags: [${tags.map(t => s"'${escapeTsSingleQuoted(t)}'").mkString(", ")}]")
    val operationIdOpt = calls
      .flatMap(_.request.operationId)
      .distinct
      .headOption
      .map(id => s"      operationId: '${escapeTsSingleQuoted(id)}'")

    val routeFields = List(
      Some(s"      method: '${httpMethod.toUpperCase()}'"),
      Some(s"      path: '${req.symbolicPath}'"),
      Some(s"      summary: '$summary'"),
      Some(s"      description: '$description'"),
      operationIdOpt,
      tagsFieldOpt,
      successStatusOpt.map(s => s"      successStatus: $s"),
      Some(s"      inputStructure: 'detailed'")
    ).flatten

    val lines = List(
      s"  $httpMethod: oc",
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
      declaredErrors(calls, errorCodeField).toList

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

  // Declared, typed errors: non-2xx calls grouped by the code extracted from their example
  // bodies. Codes match what a client-side error decoder should set on its ORPCErrors, making
  // `isDefinedError` narrowing work end to end. Calls whose body carries no extractable code
  // (bodyless 429s, non-JSON payloads) are left undeclared and surface via oRPC's defaults.
  private[orpc] def declaredErrors(
      calls: Seq[BaklavaSerializableCall],
      errorCodeField: String
  ): Option[String] = {
    val errorCalls = calls.filter(c => c.response.status.code < 200 || c.response.status.code >= 300)
    val byCode     = errorCalls
      .flatMap(c => extractErrorCode(c.response.bodyString, errorCodeField).map(_ -> c))
      .groupBy(_._1)
      .toList
      .sortBy(_._1)
    if (byCode.isEmpty) None
    else {
      val entries = byCode.map { case (code, codeCalls) =>
        val status  = codeCalls.map(_._2.response.status.code).min
        val schemas = codeCalls.flatMap(_._2.response.bodySchema).distinct.map(zod)
        val dataOpt = if (schemas.isEmpty) None else Some(s"        data: ${collapseZodUnion(schemas)}")
        val fields  = List(Some(s"        status: $status"), dataOpt).flatten.mkString(",\n")
        s"      '${escapeTsSingleQuoted(code)}': {\n$fields\n      }"
      }
      Some(s"    .errors({\n${entries.mkString(",\n")}\n    })")
    }
  }

  private def isEmptyBodyInstance(schema: BaklavaSchemaSerializable): Boolean =
    renderer.isEmptyBodyInstance(schema)

  private def toCamelCase(s: String): String = TsNaming.toCamelCase(s)

  private def escapeTsSingleQuoted(s: String): String = renderer.escapeTsSingleQuoted(s)

  private[orpc] def zod(schema: BaklavaSchemaSerializable): String = renderer.zod(schema)

  private[orpc] def collapseZodUnion(zods: Seq[String]): String = renderer.collapseZodUnion(zods)

}
