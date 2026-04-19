package pl.iterators.baklava.tsfetch

import pl.iterators.baklava.*

import java.io.{FileWriter, PrintWriter}
import scala.util.Using

/** Single-use generator: consumes the call list once and drives file writes. Computes a usage map (`className → tags that reference it`)
  * so a type used by exactly one tag lives in that tag's `types.ts`; types used by two or more tags fall into `common/types.ts`.
  */
private[tsfetch] class BaklavaTsFetchGenerator(calls: Seq[BaklavaSerializableCall]) {

  private val DefaultTag = "default"

  /** className → rendered TS interface body. Deduplicated; first occurrence wins. */
  private val interfaceBody: Map[String, String] = collectInterfaces(calls)

  /** className → directly-referenced other named classes (not recursive). */
  private val directRefs: Map[String, Set[String]] = collectDirectRefs(calls)

  /** className → set of tag names whose endpoints reference this class (directly or transitively). */
  private val usageByTag: Map[String, Set[String]] = collectUsageByTag(calls)

  /** Classes used by two or more distinct tags → `common/types.ts`. */
  private val sharedClasses: Set[String] =
    usageByTag.collect { case (name, tags) if tags.size >= 2 => name }.toSet

  /** Classes used by exactly one tag → that tag's local `types.ts`. */
  private val primaryTag: Map[String, String] =
    usageByTag.collect { case (name, tags) if tags.size == 1 => name -> tags.head }.toMap

  def writeClient(path: String): Unit = {
    val code =
      s"""/** API client configuration. Instantiate once and pass to every generated endpoint function. */
         |export interface BaklavaClientConfig {
         |  baseUrl: string;
         |  fetch?: typeof fetch;
         |  bearerToken?: string;
         |  basic?: { username: string; password: string };
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

  /** Write `common/types.ts` (if any shared types) plus each tag's `types.ts` + `endpoints.ts`. Returns the list of tag-folder names. */
  def writeTagFolders(@scala.annotation.unused sourcesDir: String, writer: (String, String) => Unit): Seq[String] = {
    val sharedSorted = sharedClasses.toSeq.sorted
    if (sharedSorted.nonEmpty)
      writer("common/types.ts", renderSharedTypesFile(sharedSorted))

    val byTag = calls.groupBy(c => c.request.operationTags.headOption.getOrElse(DefaultTag))
    byTag.keys.toSeq.sorted.map { tag =>
      val safeTag    = fileSafeTagName(tag)
      val tagClasses = primaryTag.collect { case (name, t) if t == tag => name }.toSeq.sorted
      if (tagClasses.nonEmpty)
        writer(s"$safeTag/types.ts", renderTagTypesFile(tagClasses, safeTag))
      writer(s"$safeTag/endpoints.ts", renderEndpointsFile(tag, byTag(tag), safeTag))
      safeTag
    }
  }

  def writeIndex(path: String, tagFolders: Seq[String]): Unit = {
    val lines = new scala.collection.mutable.ListBuffer[String]
    lines += """export * from "./client";"""
    if (sharedClasses.nonEmpty) lines += """export * as Common from "./common/types";"""
    tagFolders.foreach(t => lines += s"""export * from "./$t/endpoints";""")
    tagFolders.foreach { t =>
      val hasLocalTypes = primaryTag.exists { case (_, ownerTag) => fileSafeTagName(ownerTag) == t }
      if (hasLocalTypes) lines += s"""export * as ${capitalize(tsSafeIdent(t))} from "./$t/types";"""
    }
    write(path, lines.mkString("\n") + "\n")
  }

  /** `common/types.ts` — shared types. References to other shared types within this file are resolved internally (no import). */
  private def renderSharedTypesFile(classes: Seq[String]): String =
    classes.map(name => s"export interface ${tsSafeIdent(name)} ${interfaceBody(name)}").mkString("\n\n") + "\n"

  /** `<tag>/types.ts` — tag-local types. Emits imports for any shared types or types owned by a different tag that these interfaces
    * reference.
    */
  private def renderTagTypesFile(classes: Seq[String], currentTag: String): String = {
    val refs = classes.flatMap(directRefs.getOrElse(_, Set.empty)).distinct

    val fromShared    = refs.filter(sharedClasses.contains).sorted
    val fromOtherTags = refs
      .filter(c => !sharedClasses.contains(c))
      .flatMap(c => primaryTag.get(c).map(t => c -> fileSafeTagName(t)))
      .filter { case (_, otherTag) => otherTag != currentTag }
      .distinct

    val importLines = new scala.collection.mutable.ListBuffer[String]
    if (fromShared.nonEmpty)
      importLines += s"""import type { ${fromShared.map(tsSafeIdent).mkString(", ")} } from "../common/types";"""
    fromOtherTags
      .groupMap(_._2)(_._1)
      .toSeq
      .sortBy(_._1)
      .foreach { case (otherTag, cs) =>
        importLines += s"""import type { ${cs.map(tsSafeIdent).sorted.mkString(", ")} } from "../$otherTag/types";"""
      }

    val header = if (importLines.isEmpty) "" else importLines.mkString("\n") + "\n\n"
    val body   = classes.map(name => s"export interface ${tsSafeIdent(name)} ${interfaceBody(name)}").mkString("\n\n") + "\n"
    header + body
  }

  private def renderEndpointsFile(tag: String, tagCalls: Seq[BaklavaSerializableCall], tagFolder: String): String = {
    val endpoints = tagCalls
      .groupBy(c => (c.request.method.map(_.method).getOrElse("GET"), c.request.symbolicPath))
      .toSeq
      .sortBy { case ((m, p), _) => (p, m) }
      .map { case (_, endpointCalls) => renderEndpoint(endpointCalls) }

    val referencedClasses = tagCalls.flatMap(referencedClassesInCall).distinct

    val imports = new scala.collection.mutable.ListBuffer[String]
    imports += """import { BaklavaClient, BaklavaHttpError } from "../client";"""

    val localRefs = referencedClasses.filter(c => primaryTag.get(c).exists(fileSafeTagName(_) == tagFolder)).sorted
    if (localRefs.nonEmpty) imports += s"""import type { ${localRefs.map(tsSafeIdent).mkString(", ")} } from "./types";"""

    val sharedRefs = referencedClasses.filter(sharedClasses.contains).sorted
    if (sharedRefs.nonEmpty) imports += s"""import type { ${sharedRefs.map(tsSafeIdent).mkString(", ")} } from "../common/types";"""

    val otherTagRefs = referencedClasses
      .filter(c => !sharedClasses.contains(c) && primaryTag.get(c).exists(fileSafeTagName(_) != tagFolder))
      .flatMap(c => primaryTag.get(c).map(ot => c -> fileSafeTagName(ot)))
      .groupMap(_._2)(_._1)
    otherTagRefs.toSeq.sortBy(_._1).foreach { case (otherTag, cs) =>
      imports += s"""import type { ${cs.map(tsSafeIdent).sorted.mkString(", ")} } from "../$otherTag/types";"""
    }

    // Suppress "unused `tag`" warning on the unused parameter (left in for future use).
    locally(tag)
    imports.mkString("\n") + "\n\n" + endpoints.mkString("\n\n") + "\n"
  }

  private def renderEndpoint(endpointCalls: Seq[BaklavaSerializableCall]): String = {
    val head   = endpointCalls.head
    val req    = head.request
    val method = req.method.map(_.method.toUpperCase).getOrElse("GET")
    val fnName = functionName(req)
    val jsdoc  = renderJsdoc(req)

    val pathParams      = req.pathParametersSeq
    val queryParams     = req.queryParametersSeq
    val declaredHeaders = req.headersSeq.filterNot(h => isSpecialHeader(h.name))
    val bodySchema      = req.bodySchema

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

    val urlExpr = renderUrlExpression(req.symbolicPath, pathParams.map(_.name), queryParams.map(_.name))

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

  // -- Schema analysis --------------------------------------------------------

  private def collectInterfaces(calls: Seq[BaklavaSerializableCall]): Map[String, String] = {
    val collected                                      = scala.collection.mutable.LinkedHashMap.empty[String, String]
    def visit(schema: BaklavaSchemaSerializable): Unit = schema.`type` match {
      case SchemaType.ObjectType if isNamedInterface(schema) =>
        if (!collected.contains(schema.className)) collected(schema.className) = renderInterfaceBody(schema)
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
    def collectFromSchema(schema: BaklavaSchemaSerializable): Unit = schema.`type` match {
      case SchemaType.ObjectType if isNamedInterface(schema) =>
        val refs = schema.properties.values.flatMap(directReferencesIn).toSet
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

  /** Named classes directly referenced from a schema's immediate property types (doesn't descend into nested anonymous objects). */
  private def directReferencesIn(schema: BaklavaSchemaSerializable): Set[String] = schema.`type` match {
    case SchemaType.ObjectType if isNamedInterface(schema) => Set(schema.className)
    case SchemaType.ArrayType                              => schema.items.toSet.flatMap(directReferencesIn)
    case _                                                 => Set.empty
  }

  private def collectUsageByTag(calls: Seq[BaklavaSerializableCall]): Map[String, Set[String]] = {
    val usage = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
    calls.foreach { c =>
      val tag  = c.request.operationTags.headOption.getOrElse(DefaultTag)
      val refs = referencedClassesInCall(c)
      refs.foreach(cls => usage.update(cls, usage(cls) + tag))
    }
    // Also: if A is used by tag X and A contains B, B is also (transitively) used by tag X.
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
      case SchemaType.ObjectType if isNamedInterface(s) =>
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

  // -- Rendering primitives ---------------------------------------------------

  private def renderJsdoc(req: BaklavaRequestContextSerializable): String = {
    val parts = Seq(req.operationSummary, req.operationDescription).flatten.distinct
    if (parts.isEmpty) "" else s"/** ${parts.mkString(" — ")} */"
  }

  private def renderUrlExpression(symbolicPath: String, pathParamNames: Seq[String], queryParamNames: Seq[String]): String = {
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
      !schema.className.contains("[") &&
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
      if (schema.`enum`.exists(_.nonEmpty))
        schema.`enum`.get.toList.sorted.map(v => "\"" + v.replace("\"", "\\\"") + "\"").mkString(" | ")
      else "string"
    case SchemaType.ArrayType =>
      val inner = schema.items.map(tsType).getOrElse("unknown")
      s"$inner[]"
    case SchemaType.ObjectType =>
      if (isNamedInterface(schema)) tsSafeIdent(schema.className)
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

  private def isSpecialHeader(name: String): Boolean =
    Set("authorization", "content-type").contains(name.toLowerCase)

  private def tsSafeIdent(name: String): String =
    name.replaceAll("[^A-Za-z0-9_]", "_")

  private def tsFieldKey(name: String): String =
    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) name
    else "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def tsRawIdent(name: String): String =
    if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) name
    else "[" + "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]"

  private def fileSafeTagName(tag: String): String =
    tag.toLowerCase.replaceAll("[^a-z0-9]+", "-").stripPrefix("-").stripSuffix("-") match {
      case ""    => DefaultTag
      case clean => clean
    }

  private def write(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
}
