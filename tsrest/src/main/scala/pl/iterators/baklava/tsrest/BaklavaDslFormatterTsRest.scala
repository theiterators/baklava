package pl.iterators.baklava.tsrest

import pl.iterators.baklava.*
import sttp.model.Method

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

class BaklavaDslFormatterTsRest extends BaklavaDslFormatter {
  private val dirName                 = "target/baklava/tsrest"
  private val sourcesDirName          = "target/baklava/tsrest/src"
  private val packageContractJsonPath = s"$dirName/package-contracts.json"

  private val contractTsPath = s"$sourcesDirName/contracts.ts"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    // Create all target directories upfront so downstream writes don't depend on ordering.
    new File(dirName).mkdirs()
    new File(sourcesDirName).mkdirs()

    BaklavaTsRestFiles.files.foreach { case (file, content) =>
      writeTo(s"$dirName/$file", content)
    }

    config
      .get("ts-rest-package-contract-json")
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

  private def buildParamsZod[P](
      paramsPerCall: Seq[Seq[P]],
      nameOf: P => String,
      schemaOf: P => BaklavaSchemaSerializable,
      quoteKeys: Boolean
  ): Option[String] = {
    val distinctSets = paramsPerCall.distinct
    if (!distinctSets.exists(_.nonEmpty)) None
    else {
      val zds = distinctSets.map { params =>
        val fields = params.map { p =>
          val key          = if (quoteKeys) s""""${escapeTsDoubleQuoted(nameOf(p))}"""" else nameOf(p)
          val nullishMaybe = if (!schemaOf(p).required) ".nullish()" else ""
          s"$key: ${zod(schemaOf(p))}$nullishMaybe"
        }
        "z.object({" + fields.mkString(", ") + "})"
      }
      Some(collapseZodUnion(zds))
    }
  }

  /** Convert a Baklava `{name}` placeholder path to the ts-rest `:name` syntax. Non-placeholder braces (i.e. anything containing `/` or
    * nested braces) are left alone. Param names can contain any character except `{`, `}`, or `/` — so hyphens and dots survive.
    */
  private[tsrest] def toTsRestPath(symbolicPath: String): String =
    symbolicPath.replaceAll("""\{([^{}/]+)\}""", ":$1")

  private[tsrest] def contractNameFromSymbolicPath(path: String): String = {
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
    val code              =
      s"""export const $contractConstName = initContract().router({
         |${endpointsWithCalls.sortBy(_._1._1.map(_.toString).getOrElse("")).map(createContractForEndpoint).mkString(",\n")}
         |});
         |""".stripMargin
    writeTo(
      s"$sourcesDirName/$contractName.contract.ts",
      """import { z } from "zod";
        |import { initContract } from "@ts-rest/core";
        |""".stripMargin + "\n" + code
    )
    contractConstName
  }

  // Contract endpoint generator
  private def createContractForEndpoint(
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
    val path        = toTsRestPath(req.symbolicPath)

    val pathParamsZodOpt = buildParamsZod(
      calls.map(_.request.pathParametersSeq),
      (p: BaklavaPathParamSerializable) => p.name,
      (p: BaklavaPathParamSerializable) => p.schema,
      quoteKeys = false
    )
    val queryParamsZodOpt = buildParamsZod(
      calls.map(_.request.queryParametersSeq),
      (p: BaklavaQueryParamSerializable) => p.name,
      (p: BaklavaQueryParamSerializable) => p.schema,
      quoteKeys = false
    )
    val headersZodOpt = buildParamsZod(
      calls.map(_.request.headersSeq),
      (h: BaklavaHeaderSerializable) => h.name,
      (h: BaklavaHeaderSerializable) => h.schema,
      quoteKeys = true
    )
    // --- Body ---
    val bodySchemas = calls.flatMap(_.request.bodySchema).distinct
    val bodyZods    =
      if (bodySchemas.isEmpty) Seq("z.undefined()")
      else if (bodySchemas.size == 1 && isEmptyBodyInstance(bodySchemas.head)) Seq("z.undefined()")
      else {
        val notEmptyBodies = bodySchemas.filterNot(isEmptyBodyInstance)
        if (notEmptyBodies.isEmpty) Seq("z.undefined()") else notEmptyBodies.map(zod)
      }
    val bodyZod = collapseZodUnion(bodyZods)

    // --- Responses ---
    val responses = calls
      .groupBy(_.response.status.code)
      .toList
      .sortBy(_._1)
      .map { case (status, respCalls) =>
        val schemas = respCalls.flatMap(_.response.bodySchema).distinct.map(zod)
        val zodStr  = collapseZodUnion(schemas)
        s"      $status: $zodStr"
      }
      .mkString(",\n")

    val bodyLine =
      if (httpMethod.equals("get") && bodyZod == "z.undefined()") None
      else Some(s"    body: $bodyZod,")

    // Compose contract entry
    val lines = List(
      s"  $httpMethod: {",
      s"    summary: '${summary}',",
      s"    description: '${description}',",
      s"    method: '${httpMethod.toUpperCase()}',",
      s"    path: '$path',"
    ).++(pathParamsZodOpt.toList.map(z => s"    pathParams: $z,"))
      .++(queryParamsZodOpt.toList.map(z => s"    query: $z,"))
      .++(headersZodOpt.toList.map(z => s"    headers: $z,"))
      .++(bodyLine.toList)
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

  private[tsrest] def zod(schema: BaklavaSchemaSerializable): String = {
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
        if (schema.properties.isEmpty) s"z.object({})$desc"
        else {
          val props = schema.properties.toSeq
            .sortBy(_._1)
            .map { case (k, v) =>
              s""""${escapeTsDoubleQuoted(k)}": ${zod(v)}${if (!v.required) ".nullish()" else ""}"""
            }
            .mkString("\n        ", ",\n        ", "")
          s"z.object({$props})$desc"
        }
      case SchemaType.NullType => s"z.null()$desc"
    }
  }

  private[tsrest] def collapseZodUnion(zods: Seq[String]): String = {
    val distinct = zods.distinct
    if (distinct.isEmpty) "z.undefined()"
    else if (distinct.size == 1) distinct.head
    else s"z.union([${distinct.mkString(", ")}])"
  }

}
