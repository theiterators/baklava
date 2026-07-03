package pl.iterators.baklava.orpc

import pl.iterators.baklava.*
import sttp.model.Method

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

class BaklavaDslFormatterOrpc extends BaklavaDslFormatter {
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

    val contractNames = callsGroupedBySymbolicPathIntoContractName
      .map { case (name, endpoints) =>
        val constName = createContractForGroup(name, endpoints)
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
  ): Option[String] = {
    val distinctSets = paramsPerCall.distinct
    if (!distinctSets.exists(_.nonEmpty)) None
    else {
      val zds = distinctSets.map { params =>
        val fields = params.map { p =>
          val nullishMaybe = if (!schemaOf(p).required) ".nullish()" else ""
          s"${tsObjectKey(nameOf(p))}: ${zod(schemaOf(p))}$nullishMaybe"
        }
        "z.object({" + fields.mkString(", ") + "})"
      }
      Some(collapseZodUnion(zds))
    }
  }

  // A parameter group where every field is optional may be omitted by the caller entirely.
  private[orpc] def isFullyOptionalGroup[P](
      paramsPerCall: Seq[Seq[P]],
      schemaOf: P => BaklavaSchemaSerializable
  ): Boolean =
    paramsPerCall.distinct.forall(_.forall(p => !schemaOf(p).required))

  private val jsIdentifier = "[A-Za-z_$][A-Za-z0-9_$]*".r

  // An object key may be written bare only if it's a valid JS identifier; query/header/path-param
  // names can be kebab-case (`seller-id`, `X-Forwarded-For`) or start with a digit, which would
  // otherwise produce uncompilable TypeScript — so quote anything that isn't identifier-shaped.
  private[orpc] def tsObjectKey(name: String): String =
    if (jsIdentifier.matches(name)) name
    else s""""${escapeTsDoubleQuoted(name)}""""

  // Render one captured `Multipart` value as a body schema: a `z.object` keyed by part name,
  // `FilePart` -> `z.instanceof(File)`, `TextPart` -> `z.string()`. oRPC serializes a body
  // containing `File` values as `multipart/form-data` on the wire. A repeated part name
  // (a multi-value form field) becomes a `z.array(...)`; a name that mixes file and text parts
  // unions the element schemas. Names and element schemas are sorted so output is deterministic.
  private[orpc] def renderMultipartBody(parts: Seq[BaklavaMultipartPartSerializable]): String = {
    val fields = parts
      .groupBy(_.name)
      .toSeq
      .sortBy(_._1)
      .map { case (name, ps) =>
        val element = collapseZodUnion(ps.map(p => if (p.isFile) "z.instanceof(File)" else "z.string()").sorted)
        val schema  = if (ps.size > 1) s"z.array($element)" else element
        s"${tsObjectKey(name)}: $schema"
      }
    s"z.object({${fields.mkString(", ")}})"
  }

  private[orpc] def contractNameFromSymbolicPath(path: String): String = {
    val cleaned = path.stripPrefix("/").stripSuffix("/")
    if (cleaned.isEmpty) "root"
    else {
      cleaned
        .split("/")
        .map {
          case p if p.startsWith("{") && p.endsWith("}") => "--" + p.substring(1, p.length - 1)
          case p if p.startsWith(":")                    => "--" + p.substring(1)
          case p                                         => p
        }
        .mkString("-")
        .replace(".", "---")
    }
  }

  private def createContractForGroup(
      contractName: String,
      endpointsWithCalls: Seq[((Option[Method], String), Seq[BaklavaSerializableCall])]
  ): String = {
    val contractConstName = toCamelCase(contractName) + "Contract"
    val errorsConstName   = toCamelCase(contractName) + "Errors"
    val sortedEndpoints   = endpointsWithCalls.sortBy(_._1._1.map(_.toString).getOrElse(""))

    val code =
      s"""export const $contractConstName = {
         |${sortedEndpoints.map(createContractForEndpoint).mkString(",\n")}
         |};
         |""".stripMargin

    // Error responses don't follow oRPC's own error envelope (the backend is not an oRPC
    // server), so they are not declared via `.errors()`. Instead each contract exports a
    // per-method, per-status map of zod schemas — pair it with OpenAPILink's
    // `customErrorResponseBodyDecoder`, or parse `error.data` at the call site.
    val errorEntries = sortedEndpoints.flatMap(createErrorsForEndpoint)
    val errorsCode   =
      if (errorEntries.isEmpty) ""
      else
        s"""
           |export const $errorsConstName = {
           |${errorEntries.mkString(",\n")}
           |};
           |""".stripMargin

    writeTo(
      s"$sourcesDirName/$contractName.contract.ts",
      """import { z } from "zod";
        |import { oc } from "@orpc/contract";
        |""".stripMargin + "\n" + code + errorsCode
    )
    contractConstName
  }

  private def successStatuses(calls: Seq[BaklavaSerializableCall]): Seq[Int] =
    calls.map(_.response.status.code).filter(c => c >= 200 && c < 300).distinct.sorted

  // Contract endpoint generator: one `<method>: oc.route({...}).input(...).output(...)` entry.
  private[orpc] def createContractForEndpoint(
      endpoint: ((Option[Method], String), Seq[BaklavaSerializableCall])
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

    val routeFields = List(
      Some(s"      method: '${httpMethod.toUpperCase()}'"),
      Some(s"      path: '${req.symbolicPath}'"),
      Some(s"      summary: '$summary'"),
      Some(s"      description: '$description'"),
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
      outputZodOpt.toList.map(z => s"    .output($z)")

    lines.mkString("\n")
  }

  // Non-2xx responses captured for one endpoint, as `<method>: { <status>: <zod> }`.
  private[orpc] def createErrorsForEndpoint(
      endpoint: ((Option[Method], String), Seq[BaklavaSerializableCall])
  ): Option[String] = {
    val ((httpMethodOpt, _), calls) = endpoint
    val httpMethod                  = httpMethodOpt.map(_.method).getOrElse("ANY").toLowerCase
    val errorCalls                  = calls.filter(c => c.response.status.code < 200 || c.response.status.code >= 300)
    if (errorCalls.isEmpty) None
    else {
      val byStatus = errorCalls
        .groupBy(_.response.status.code)
        .toList
        .sortBy(_._1)
        .map { case (status, respCalls) =>
          val schemas = respCalls.flatMap(_.response.bodySchema).distinct.map(zod)
          val zodStr  = if (schemas.isEmpty) "z.void()" else collapseZodUnion(schemas)
          s"    $status: $zodStr"
        }
      Some(s"""  $httpMethod: {
              |${byStatus.mkString(",\n")}
              |  }""".stripMargin)
    }
  }

  private def isEmptyBodyInstance(schema: BaklavaSchemaSerializable): Boolean =
    schema.`type` == SchemaType.StringType &&
      schema.`enum`.exists(enums => enums.contains("EmptyBodyInstance") && enums.size == 1)

  private def toCamelCase(s: String): String = {
    val base = s.replaceAll("--", "-")
    base
      .split("-")
      .filter(_.nonEmpty)
      .zipWithIndex
      .map { case (s, i) =>
        if (i == 0) s.toLowerCase else s.capitalize
      }
      .mkString
  }

  private def escapeTsSingleQuoted(s: String): String =
    s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")

  private def escapeTsDoubleQuoted(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private[orpc] def zod(schema: BaklavaSchemaSerializable): String = {
    val desc = schema.description.map(d => s""".describe("${escapeTsDoubleQuoted(d)}")""").getOrElse("")
    schema.`type` match {
      case SchemaType.StringType =>
        if (schema.`enum`.exists(_.nonEmpty)) {
          // Sort for deterministic output; escape for double-quoted TS string context.
          val e = schema.`enum`.get.toList.sorted.map(s => "\"" + escapeTsDoubleQuoted(s) + "\"").mkString(",")
          s"z.enum([$e])$desc"
        } else if (schema.format.contains("email")) s"z.string().email()$desc"
        else if (schema.format.contains("uuid")) s"z.string().uuid()$desc"
        else if (schema.format.contains("date-time")) s"z.coerce.date()$desc"
        else s"z.string()$desc"
      case SchemaType.BooleanType => s"z.boolean()$desc"
      case SchemaType.IntegerType => s"z.number().int()$desc"
      case SchemaType.NumberType  => s"z.number()$desc"
      case SchemaType.ArrayType   =>
        val item = schema.items.map(zod).getOrElse("z.any()")
        s"z.array($item)$desc"
      case SchemaType.ObjectType =>
        val objectBody =
          if (schema.properties.isEmpty) "z.object({})"
          else {
            val props = schema.properties.toSeq
              .sortBy(_._1)
              .map { case (k, v) =>
                s""""${escapeTsDoubleQuoted(k)}": ${zod(v)}${if (!v.required) ".nullish()" else ""}"""
              }
              .mkString("\n        ", ",\n        ", "")
            s"z.object({$props})"
          }
        schema.additionalPropertiesSchema match {
          // A map-like object: all values conform to one schema -> z.record (keys are strings in JSON).
          case Some(v) if schema.properties.isEmpty => s"z.record(z.string(), ${zod(v)})$desc"
          case Some(v)                              => s"$objectBody.catchall(${zod(v)})$desc"
          case None                                 => s"$objectBody$desc"
        }
      case SchemaType.NullType => s"z.null()$desc"
    }
  }

  private[orpc] def collapseZodUnion(zods: Seq[String]): String = {
    val distinct = zods.distinct
    if (distinct.isEmpty) "z.void()"
    else if (distinct.size == 1) distinct.head
    else s"z.union([${distinct.mkString(", ")}])"
  }

}
