package pl.iterators.baklava.tsrest

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}

class BaklavaDslFormatterTsRest extends BaklavaDslFormatter {
  private val dirName                 = "target/baklava/tsrest"
  private val sourcesDirName          = "target/baklava/tsrest/src"
  private val packageContractJsonPath = s"$dirName/package-contracts.json"

  private val contractTsPath = s"$sourcesDirName/contracts.ts"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    new File(dirName).mkdirs()

    BaklavaTsRestFiles.files.foreach { case (file, content) =>
      val out = new PrintWriter(new FileWriter(s"$dirName/$file"))
      out.write(content)
      out.close()
    }

    config
      .get("ts-rest-package-contract-json")
      .foreach { packageContractJson =>
        val out = new PrintWriter(new FileWriter(packageContractJsonPath))
        out.write(packageContractJson)
        out.close()
      }

    new File(sourcesDirName).mkdirs()

    val out = new PrintWriter(new FileWriter(contractTsPath))

    val callsGroupedBySymbolicPathIntoContractName = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .groupBy(c => contractNameFromSymbolicPath(c._1._2))
      .toList
      .sortBy(_._1)

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

    out.write(
      s"""$importStmts

         |export const contracts: {
         |$typeMap
         |} = {
         |$contractsMap
         |};
         |\n""".stripMargin
    )
    out.close()

  }

  private def contractNameFromSymbolicPath(path: String): String = {
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
      endpointsWithCalls: Seq[((Option[BaklavaHttpMethod], String), Seq[BaklavaSerializableCall])]
  ): String = {
    val contractConstName = toCamelCase(contractName) + "Contract"
    val code =
      s"""export const $contractConstName = initContract().router({
         |${endpointsWithCalls.sortBy(_._1._1.map(_.toString).getOrElse("")).map(createContractForEndpoint).mkString(",\n")}
         |});
         |""".stripMargin
    val file = new FileWriter(s"$sourcesDirName/$contractName.contract.ts")
    file.write("""import { z } from "zod";
                 |import { initContract } from "@ts-rest/core";
                 |""".stripMargin + "\n" + code)
    file.close()
    contractConstName
  }

  // Contract endpoint generator
  private def createContractForEndpoint(
      endpoint: ((Option[BaklavaHttpMethod], String), Seq[BaklavaSerializableCall])
  ): String = {
    val ((httpMethodOpt, symbolicPath), calls) = endpoint
    val httpMethod                             = httpMethodOpt.map(_.value).getOrElse("ANY").toLowerCase

    val firstCall   = calls.head
    val req         = firstCall.request
    val summary     = req.operationSummary.getOrElse("")
    val description = req.operationDescription.getOrElse("")
    val path        = req.symbolicPath.replaceAll("\\{", ":").replaceAll("}", "")

    // --- Path Params ---
    val pathParamsSchemas = calls.map(_.request.pathParametersSeq).distinct
    val showPathParams    = pathParamsSchemas.exists(_.nonEmpty)
    val pathParamsZodOpt =
      if (!showPathParams) None
      else {
        val zds =
          if (pathParamsSchemas.size == 1)
            Seq("z.object({" + pathParamsSchemas.head.map(p => s"${p.name}: ${zod(p.schema)}").mkString(", ") + "})")
          else
            pathParamsSchemas.map { params =>
              "z.object({" + params.map(p => s"${p.name}: ${zod(p.schema)}").mkString(", ") + "})"
            }
        Some(collapseZodUnion(zds))
      }

    // --- Query Params ---
    val queryParamsSchemas = calls.map(_.request.queryParametersSeq).distinct
    val showQueryParams    = queryParamsSchemas.exists(_.nonEmpty)
    val queryParamsZodOpt =
      if (!showQueryParams) None
      else {
        val zds =
          if (queryParamsSchemas.size == 1)
            Seq("z.object({" + queryParamsSchemas.head.map(p => s"${p.name}: ${zod(p.schema)}").mkString(", ") + "})")
          else
            queryParamsSchemas.map { params =>
              "z.object({" + params.map(p => s"${p.name}: ${zod(p.schema)}").mkString(", ") + "})"
            }
        Some(collapseZodUnion(zds))
      }

    // --- Headers ---
    val headersSchemas = calls.map(_.request.headersSeq).distinct
    val showHeaders    = headersSchemas.exists(_.nonEmpty)
    val headersZodOpt =
      if (!showHeaders) None
      else {
        val zds =
          if (headersSchemas.size == 1)
            Seq("z.object({" + headersSchemas.head.map(p => s""""${p.name}": ${zod(p.schema)}""").mkString(", ") + "})")
          else
            headersSchemas.map { params =>
              "z.object({" + params.map(p => s""""${p.name}": ${zod(p.schema)}""").mkString(", ") + "})"
            }
        Some(collapseZodUnion(zds))
      }

    // --- Body ---
    val bodySchemas = calls.flatMap(_.request.bodySchema).distinct
    val bodyZods =
      if (bodySchemas.isEmpty) Seq("z.undefined()")
      else if (bodySchemas.size == 1 && isEmptyBodyInstance(bodySchemas.head)) Seq("z.undefined()")
      else {
        val notEmptyBodies = bodySchemas.filterNot(isEmptyBodyInstance)
        if (notEmptyBodies.isEmpty) Seq("z.undefined()") else notEmptyBodies.map(zod)
      }
    val bodyZod = collapseZodUnion(bodyZods)

    // --- Responses ---
    val responses = calls
      .groupBy(_.response.status.status)
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

  private def zod(schema: BaklavaSchemaSerializable): String = {
    val desc = schema.description.map(d => s""".describe("${d.replace("\"", "'").replace("\n", "\\n")}")""").getOrElse("")
    schema.`type` match {
      case SchemaType.StringType =>
        if (schema.`enum`.exists(_.nonEmpty)) {
          val e = schema.`enum`.get.map(s => "\"" + s + "\"").mkString(",")
          s"z.enum([$e])$desc"
        } else if (schema.format.contains("email")) s"z.string().email()$desc"
        else if (schema.format.contains("date-time")) s"z.coerce.date()$desc"
        else s"z.string()$desc"
      case SchemaType.BooleanType => s"z.boolean()$desc"
      case SchemaType.IntegerType => s"z.number().int()$desc"
      case SchemaType.NumberType  => s"z.number()$desc"
      case SchemaType.ArrayType =>
        val item = schema.items.map(zod).getOrElse("z.any()")
        s"z.array($item)$desc"
      case SchemaType.ObjectType =>
        if (schema.properties.isEmpty) s"z.object({})$desc"
        else {
          val props = schema.properties.toSeq
            .sortBy(_._1)
            .map { case (k, v) => s"$k: ${zod(v)}${if (!v.required) ".optional()" else ""}" }
            .mkString("\n        ", ",\n        ", "")
          s"z.object({$props})$desc"
        }
      case SchemaType.NullType => s"z.null()$desc"
    }
  }

  private def collapseZodUnion(zods: Seq[String]): String = {
    val nonTrivial = zods.filter(z => z.startsWith("z.object"))
    if (nonTrivial.size == 1) nonTrivial.head
    else if (zods.size == 1) zods.head
    else s"z.union([${zods.mkString(", ")}])"
  }

}
