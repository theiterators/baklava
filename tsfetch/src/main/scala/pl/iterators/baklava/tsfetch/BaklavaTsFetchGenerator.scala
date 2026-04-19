package pl.iterators.baklava.tsfetch

import pl.iterators.baklava.*

import java.io.{FileWriter, PrintWriter}
import scala.util.Using

/** Single-use generator: consumes the call list once and drives file writes. Kept as a class (not an object) so it can cache the derived
  * TS-interface map across all three `writeXxx` methods.
  */
private[tsfetch] class BaklavaTsFetchGenerator(calls: Seq[BaklavaSerializableCall]) {

  /** Every named object schema that appears anywhere in the calls → its TS interface body. Deduplicated by className; later occurrences
    * with different shapes win silently (matches the existing OpenAPI generator's first-schema-wins policy).
    */
  private val namedInterfaces: Map[String, String] = {
    val collected                                      = scala.collection.mutable.LinkedHashMap.empty[String, String]
    def visit(schema: BaklavaSchemaSerializable): Unit = schema.`type` match {
      case SchemaType.ObjectType if isNamedInterface(schema) =>
        if (!collected.contains(schema.className))
          collected(schema.className) = renderInterfaceBody(schema)
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

  /** Client + error class. Users instantiate `BaklavaClient` with their base URL and (optionally) credentials; all generated functions
    * take it as the first argument.
    */
  def writeClient(path: String): Unit = {
    // `s"""` so Scala stops suspecting we meant string interpolation. Every `$` in the TS source
    // (template literals) is escaped as `\$` below.
    val code =
      s"""/** API client configuration. Instantiate once and pass to every generated endpoint function. */
         |export interface BaklavaClientConfig {
         |  /** Base URL for every request, e.g. "https://api.example.com". No trailing slash. */
         |  baseUrl: string;
         |  /** Override for `fetch`. Defaults to `globalThis.fetch` — override for Node < 18 or to inject instrumentation. */
         |  fetch?: typeof fetch;
         |  /** Bearer / OAuth / OpenID Connect token used by any scheme that sends `Authorization: Bearer <token>`. */
         |  bearerToken?: string;
         |  /** HTTP Basic credentials. */
         |  basic?: { username: string; password: string };
         |  /** API key values keyed by the scheme's declared header / query / cookie name. */
         |  apiKeys?: Record<string, string>;
         |}
         |
         |export class BaklavaClient {
         |  readonly baseUrl: string;
         |  readonly fetch: typeof fetch;
         |  readonly bearerToken?: string;
         |  readonly basic?: { username: string; password: string };
         |  readonly apiKeys?: Record<string, string>;
         |
         |  constructor(config: BaklavaClientConfig) {
         |    this.baseUrl     = config.baseUrl.replace(/\\/+$$/, "");
         |    this.fetch       = config.fetch ?? (globalThis.fetch?.bind(globalThis) as typeof fetch);
         |    this.bearerToken = config.bearerToken;
         |    this.basic       = config.basic;
         |    this.apiKeys     = config.apiKeys;
         |  }
         |
         |  /** Headers contributed by any declared scheme that writes into `Authorization` or an API key header. Schemes that put
         |   *  credentials in query string or cookie produce no header here — the generated endpoint functions set those inline.
         |   */
         |  authHeaders(): Record<string, string> {
         |    const h: Record<string, string> = {};
         |    if (this.bearerToken) h["Authorization"] = `Bearer $${this.bearerToken}`;
         |    else if (this.basic)  h["Authorization"] = `Basic $${btoa(`$${this.basic.username}:$${this.basic.password}`)}`;
         |    return h;
         |  }
         |}
         |
         |export class BaklavaHttpError extends Error {
         |  constructor(public readonly status: number, public readonly body: string, message?: string) {
         |    super(message ?? `HTTP $${status}: $${body}`);
         |    this.name = "BaklavaHttpError";
         |  }
         |}
         |""".stripMargin
    write(path, code)
  }

  def writeTypes(path: String): Unit = {
    val body =
      if (namedInterfaces.isEmpty) "// (no named interfaces in this API)\n"
      else {
        namedInterfaces.toSeq
          .sortBy(_._1)
          .map { case (name, ifaceBody) => s"export interface ${tsSafeIdent(name)} $ifaceBody" }
          .mkString("\n\n") + "\n"
      }
    write(path, body)
  }

  /** One TS file per operation tag (or `default.ts` for untagged calls). Returns the list of generated tag names for the index. */
  def writeTagFiles(sourcesDir: String): Seq[String] = {
    val byTag = calls
      .groupBy(c => c.request.operationTags.headOption.getOrElse("default"))

    byTag.keys.toSeq.sorted.map { tag =>
      val tagCalls  = byTag(tag).sortBy(c => (c.request.symbolicPath, c.request.method.map(_.method).getOrElse("")))
      val endpoints = tagCalls
        .groupBy(c => (c.request.method.map(_.method).getOrElse("GET"), c.request.symbolicPath))
        .toSeq
        .sortBy { case ((m, p), _) => (p, m) }
        .map { case (_, endpointCalls) => renderEndpoint(endpointCalls) }

      val imports = List(
        """import { BaklavaClient, BaklavaHttpError } from "./client";""",
        if (namedInterfaces.nonEmpty) """import type * as T from "./types";""" else ""
      ).filter(_.nonEmpty).mkString("\n")

      val filename = fileSafeTagName(tag) + ".ts"
      write(s"$sourcesDir/$filename", imports + "\n\n" + endpoints.mkString("\n\n") + "\n")
      fileSafeTagName(tag)
    }
  }

  def writeIndex(path: String, tagFiles: Seq[String]): Unit = {
    val lines = Seq(
      """export * from "./client";""",
      if (namedInterfaces.nonEmpty) """export * as T from "./types";""" else ""
    ) ++ tagFiles.map(t => s"""export * from "./$t";""")
    write(path, lines.filter(_.nonEmpty).mkString("\n") + "\n")
  }

  private def renderEndpoint(endpointCalls: Seq[BaklavaSerializableCall]): String = {
    val head   = endpointCalls.head
    val req    = head.request
    val method = req.method.map(_.method.toUpperCase).getOrElse("GET")
    val path   = req.symbolicPath
    val fnName = functionName(req)
    val jsdoc  = renderJsdoc(req)

    val pathParams      = req.pathParametersSeq
    val queryParams     = req.queryParametersSeq
    val declaredHeaders = req.headersSeq.filterNot(h => isSpecialHeader(h.name))
    val bodySchema      = req.bodySchema

    // Params argument is a single options object; always optional if nothing required.
    val paramFields = (
      pathParams.map(p => (p.name, tsType(p.schema), true)) ++
        queryParams.map(p => (p.name, tsType(p.schema), p.schema.required)) ++
        declaredHeaders.map(h => (h.name, tsType(h.schema), h.schema.required)) ++
        bodySchema.toSeq.filterNot(isEmptyBodyInstance).map(s => ("body", tsType(s), true))
    )
    val anyParamRequired  = paramFields.exists(_._3)
    val paramsArgOptional = !anyParamRequired

    val paramsType =
      if (paramFields.isEmpty) "Record<string, never>"
      else {
        val fields = paramFields
          .map { case (name, t, required) =>
            val q = if (required) "" else "?"
            s"  ${tsFieldKey(name)}$q: $t;"
          }
          .mkString("\n")
        s"{\n$fields\n}"
      }

    val returnType = tsReturnType(endpointCalls)
    val sigParams  =
      if (paramFields.isEmpty) "_client: BaklavaClient"
      else s"client: BaklavaClient, params${if (paramsArgOptional) "?" else ""}: $paramsType"

    // Build URL: path template substitution + query string
    val urlExpr = renderUrlExpression(path, pathParams.map(_.name), queryParams.map(_.name))

    // Headers: auth + per-request declared
    val headerLines = {
      val parts = new scala.collection.mutable.ListBuffer[String]
      parts += "    ...client.authHeaders(),"
      declaredHeaders.foreach { h =>
        val key  = h.name
        val cond =
          if (h.schema.required) s"""    "$key": String(params.${tsRawIdent(h.name)}),"""
          else s"""    ...(params?.${tsRawIdent(h.name)} !== undefined ? { "$key": String(params.${tsRawIdent(h.name)}) } : {}),"""
        parts += cond
      }
      if (bodySchema.exists(!isEmptyBodyInstance(_))) parts += """    "Content-Type": "application/json","""
      // API-key-in-header schemes that don't map to Authorization
      req.securitySchemes.foreach { scheme =>
        scheme.security.apiKeyInHeader.foreach { k =>
          parts += s"""    ...(client.apiKeys?.["${k.name}"] ? { "${k.name}": client.apiKeys["${k.name}"] } : {}),"""
        }
      }
      parts.toList
    }

    val bodyLine =
      if (bodySchema.exists(!isEmptyBodyInstance(_))) Some("    body: JSON.stringify(params.body),")
      else None

    val fetchCall =
      s"""  const res = await client.fetch(url.toString(), {
         |    method: "$method",
         |    headers: {
         |${headerLines.mkString("\n")}
         |    },
         |${bodyLine.getOrElse("")}
         |  });""".stripMargin.replaceAll("\n\\s*\n", "\n")

    val handleRes =
      if (returnType == "void")
        """  if (!res.ok) throw new BaklavaHttpError(res.status, await res.text());""".stripMargin
      else
        """  const text = await res.text();
          |  if (!res.ok) throw new BaklavaHttpError(res.status, text);
          |  return (text ? JSON.parse(text) : undefined) as typeof __ret;""".stripMargin

    val retDecl = if (returnType != "void") s"  let __ret!: $returnType;\n" else ""

    s"""$jsdoc
       |export async function $fnName($sigParams): Promise<$returnType> {
       |$urlExpr
       |$retDecl$fetchCall
       |$handleRes
       |}""".stripMargin
  }

  private def renderJsdoc(req: BaklavaRequestContextSerializable): String = {
    val parts = Seq(
      req.operationSummary,
      req.operationDescription
    ).flatten.distinct
    if (parts.isEmpty) "" else s"/** ${parts.mkString(" — ")} */"
  }

  private def renderUrlExpression(
      symbolicPath: String,
      pathParamNames: Seq[String],
      queryParamNames: Seq[String]
  ): String = {
    val filled = pathParamNames.foldLeft(symbolicPath) { (acc, name) =>
      acc.replace(s"{$name}", s"$${encodeURIComponent(String(params.${tsRawIdent(name)}))}")
    }
    val urlLine    = s"""  const url = new URL(`$${client.baseUrl}$filled`);"""
    val queryLines = queryParamNames.map { name =>
      s"""  if (params?.${tsRawIdent(name)} !== undefined) url.searchParams.set("$name", String(params.${tsRawIdent(name)}));"""
    }
    (urlLine +: queryLines).mkString("\n")
  }

  private def tsReturnType(endpointCalls: Seq[BaklavaSerializableCall]): String = {
    val successCalls = endpointCalls.filter(c => c.response.status.code >= 200 && c.response.status.code < 300)
    val picked       = if (successCalls.nonEmpty) successCalls else endpointCalls
    val schemas      = picked.flatMap(_.response.bodySchema).filterNot(isEmptyBodyInstance).distinct
    if (schemas.isEmpty) "void" else tsType(schemas.head)
  }

  private def isEmptyBodyInstance(schema: BaklavaSchemaSerializable): Boolean =
    schema.`type` == SchemaType.StringType &&
      schema.`enum`.exists(enums => enums.contains("EmptyBodyInstance") && enums.size == 1)

  private def isNamedInterface(schema: BaklavaSchemaSerializable): Boolean =
    schema.`type` == SchemaType.ObjectType &&
      schema.properties.nonEmpty &&
      !schema.className.contains("[") && // collection types like List[T]
      !Set("FormData", "UrlForm", "Multipart").contains(schema.className)

  private def renderInterfaceBody(schema: BaklavaSchemaSerializable): String = {
    val fields = schema.properties.toSeq.sortBy(_._1).map { case (name, s) =>
      val q = if (s.required) "" else "?"
      s"  ${tsFieldKey(name)}$q: ${tsType(s)};"
    }
    "{\n" + fields.mkString("\n") + "\n}"
  }

  private def tsType(schema: BaklavaSchemaSerializable): String = schema.`type` match {
    case SchemaType.NullType    => "null"
    case SchemaType.BooleanType => "boolean"
    case SchemaType.IntegerType => "number"
    case SchemaType.NumberType  => "number"
    case SchemaType.StringType  =>
      if (schema.`enum`.exists(_.nonEmpty)) schema.`enum`.get.toList.sorted.map(v => "\"" + v.replace("\"", "\\\"") + "\"").mkString(" | ")
      else "string"
    case SchemaType.ArrayType =>
      val inner = schema.items.map(tsType).getOrElse("unknown")
      s"$inner[]"
    case SchemaType.ObjectType =>
      if (isNamedInterface(schema)) s"T.${tsSafeIdent(schema.className)}"
      else if (schema.properties.isEmpty) "Record<string, unknown>"
      else renderInterfaceBody(schema)
  }

  private def functionName(req: BaklavaRequestContextSerializable): String =
    req.operationId.map(tsSafeIdent).getOrElse {
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

  private def capitalize(s: String): String =
    if (s.isEmpty) s else s"${s.charAt(0).toUpper}${s.substring(1)}"

  /** The subset of header names the client manages internally (`Authorization` via `authHeaders`, `Content-Type` from body). Everything
    * else the user declares becomes a typed function parameter.
    */
  private def isSpecialHeader(name: String): Boolean =
    Set("authorization", "content-type").contains(name.toLowerCase)

  /** Identifier-safe form of a Scala type name for use as a TS identifier. Strips generics and non-word chars. */
  private def tsSafeIdent(name: String): String =
    name.replaceAll("[^A-Za-z0-9_]", "_")

  /** Field-name safe form: quote the key when it's not a valid bare JS identifier. */
  private def tsFieldKey(name: String): String =
    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) name else "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  /** Identifier usable as a property access (for `params.xxx`). When the field key had to be quoted, use bracket form instead. */
  private def tsRawIdent(name: String): String =
    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) name else "[" + "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"

  private def fileSafeTagName(tag: String): String =
    tag.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-") match {
      case ""    => "default"
      case clean => clean
    }

  private def write(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
}
