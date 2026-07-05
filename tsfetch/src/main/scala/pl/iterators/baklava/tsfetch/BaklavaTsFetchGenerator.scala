package pl.iterators.baklava.tsfetch

import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.TsPathRouter
import pl.iterators.baklava.tscommon.TsPathRouter.RouterModule
import sttp.model.Method

import java.io.{FileWriter, PrintWriter}
import scala.util.Using

/** Single-use generator: consumes the call list once and drives file writes. Endpoints group into route-area modules (shared
  * [[TsPathRouter]] boundaries, the same ones the ts-rest and oRPC formats use). Computes a usage map (`className → modules that reference
  * it`) so a type used by exactly one module lives in that module's `types.ts`; types used by two or more modules fall into
  * `common/types.ts`.
  */
private[tsfetch] class BaklavaTsFetchGenerator(calls: Seq[BaklavaSerializableCall]) {

  private val modules: Seq[RouterModule] = {
    // Sorted so tree insertion (and thus collision-suffix assignment) is deterministic.
    val endpoints = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .sortBy { case ((method, path), _) => (path, method.map(_.toString).getOrElse("")) }
    TsPathRouter.modulesOf(TsPathRouter.buildRouterTree(endpoints))
  }

  private val moduleIdByEndpoint: Map[(Option[Method], String), String] =
    modules.flatMap(m => TsPathRouter.endpointsOf(m.node).map(_._1 -> m.constName)).toMap

  private def moduleIdOf(c: BaklavaSerializableCall): String =
    moduleIdByEndpoint((c.request.method, c.request.symbolicPath))

  private val folderById: Map[String, String] =
    modules.map(m => m.constName -> m.fileSegments.mkString("/")).toMap

  /** Relative prefix from inside a module folder back to `src/` (module folders can nest, e.g. `v1/auctions`). */
  private def upPrefix(moduleId: String): String = "../" * (folderById(moduleId).count(_ == '/') + 1)

  /** className → rendered TS interface body. First occurrence wins; later schemas with the same `className` are ignored. */
  private val interfaceBody: Map[String, String] = collectInterfaces(calls)

  /** className → directly-referenced other named classes (not recursive). */
  private val directRefs: Map[String, Set[String]] = collectDirectRefs(calls)

  /** className → set of module ids whose endpoints reference this class (directly or transitively). */
  private val usageByModule: Map[String, Set[String]] = collectUsageByModule(calls)

  /** Classes used by two or more distinct modules → `common/types.ts`. */
  private val sharedClasses: Set[String] =
    usageByModule.collect { case (name, ids) if ids.size >= 2 => name }.toSet

  /** Classes used by exactly one module → that module's local `types.ts`. */
  private val primaryModule: Map[String, String] =
    usageByModule.collect { case (name, ids) if ids.size == 1 => name -> ids.head }.toMap

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
         |function resolveFetch(configured?: typeof fetch): typeof fetch {
         |  if (configured) return configured;
         |  const g = globalThis.fetch;
         |  if (g) return g.bind(globalThis) as typeof fetch;
         |  throw new Error(
         |    "BaklavaClient: no fetch implementation available. " +
         |    "Pass `fetch` in BaklavaClientConfig (e.g. node-fetch or undici) on Node < 18."
         |  );
         |}
         |
         |function b64Encode(raw: string): string {
         |  const g = globalThis as { btoa?: (s: string) => string; Buffer?: { from(s: string, enc: string): { toString(enc: string): string } } };
         |  if (g.btoa) return g.btoa(raw);
         |  if (g.Buffer) return g.Buffer.from(raw, "utf-8").toString("base64");
         |  throw new Error("BaklavaClient: no base64 encoder available (btoa/Buffer).");
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
         |    this.fetch       = resolveFetch(config.fetch);
         |    this.bearerToken = config.bearerToken;
         |    this.basic       = config.basic;
         |    this.apiKeys     = config.apiKeys;
         |  }
         |
         |  authHeaders(): Record<string, string> {
         |    const h: Record<string, string> = {};
         |    if (this.bearerToken) h["Authorization"] = `Bearer $${this.bearerToken}`;
         |    else if (this.basic)  h["Authorization"] = `Basic $${b64Encode(`$${this.basic.username}:$${this.basic.password}`)}`;
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

  /** Write `common/types.ts` (if any shared types) plus each module's `types.ts` + `endpoints.ts`. */
  def writeModuleFolders(writer: (String, String) => Unit): Unit = {
    val sharedSorted = sharedClasses.toSeq.sorted
    if (sharedSorted.nonEmpty)
      writer("common/types.ts", renderSharedTypesFile(sharedSorted))

    modules.foreach { module =>
      val folder        = folderById(module.constName)
      val moduleClasses = primaryModule.collect { case (name, id) if id == module.constName => name }.toSeq.sorted
      val moduleCalls   = TsPathRouter.endpointsOf(module.node).flatMap(_._2)
      if (moduleClasses.nonEmpty)
        writer(s"$folder/types.ts", renderModuleTypesFile(moduleClasses, module.constName))
      writer(s"$folder/endpoints.ts", renderEndpointsFile(moduleCalls, module.constName))
    }
  }

  def writeIndex(path: String): Unit = {
    val lines = new scala.collection.mutable.ListBuffer[String]
    lines += """export * from "./client";"""
    if (sharedClasses.nonEmpty) lines += """export * as Common from "./common/types";"""
    modules.foreach(m => lines += s"""export * from "./${folderById(m.constName)}/endpoints";""")
    modules.foreach { m =>
      val hasLocalTypes = primaryModule.exists { case (_, id) => id == m.constName }
      if (hasLocalTypes) lines += s"""export * as ${capitalize(m.constName)} from "./${folderById(m.constName)}/types";"""
    }
    write(path, lines.mkString("\n") + "\n")
  }

  /** `common/types.ts` — shared types. References to other shared types within this file are resolved internally (no import). */
  private def renderSharedTypesFile(classes: Seq[String]): String =
    classes.map(name => s"export interface ${tsSafeIdent(name)} ${interfaceBody(name)}").mkString("\n\n") + "\n"

  /** `<module>/types.ts` — module-local types. Emits imports for any shared types or types owned by a different module that these
    * interfaces reference.
    */
  private def renderModuleTypesFile(classes: Seq[String], moduleId: String): String = {
    val refs = classes.flatMap(directRefs.getOrElse(_, Set.empty)).distinct
    val up   = upPrefix(moduleId)

    val fromShared       = refs.filter(sharedClasses.contains).sorted
    val fromOtherModules = refs
      .filter(c => !sharedClasses.contains(c))
      .flatMap(c => primaryModule.get(c).map(id => c -> id))
      .filter { case (_, otherId) => otherId != moduleId }
      .distinct

    val importLines = new scala.collection.mutable.ListBuffer[String]
    if (fromShared.nonEmpty)
      importLines += s"""import type { ${fromShared.map(tsSafeIdent).mkString(", ")} } from "${up}common/types";"""
    fromOtherModules
      .groupMap(_._2)(_._1)
      .toSeq
      .sortBy(_._1)
      .foreach { case (otherId, cs) =>
        importLines += s"""import type { ${cs.map(tsSafeIdent).sorted.mkString(", ")} } from "$up${folderById(otherId)}/types";"""
      }

    val header = if (importLines.isEmpty) "" else importLines.mkString("\n") + "\n\n"
    val body   = classes.map(name => s"export interface ${tsSafeIdent(name)} ${interfaceBody(name)}").mkString("\n\n") + "\n"
    header + body
  }

  private def renderEndpointsFile(moduleCalls: Seq[BaklavaSerializableCall], moduleId: String): String = {
    val endpoints = moduleCalls
      .groupBy(c => (c.request.method.map(_.method).getOrElse("GET"), c.request.symbolicPath))
      .toSeq
      .sortBy { case ((m, p), _) => (p, m) }
      .map { case (_, endpointCalls) => renderEndpoint(endpointCalls) }

    val referencedClasses = moduleCalls.flatMap(referencedClassesInCall).distinct
    val up                = upPrefix(moduleId)

    val imports = new scala.collection.mutable.ListBuffer[String]
    imports += s"""import { BaklavaClient, BaklavaHttpError } from "${up}client";"""

    val localRefs = referencedClasses.filter(c => primaryModule.get(c).contains(moduleId)).sorted
    if (localRefs.nonEmpty) imports += s"""import type { ${localRefs.map(tsSafeIdent).mkString(", ")} } from "./types";"""

    val sharedRefs = referencedClasses.filter(sharedClasses.contains).sorted
    if (sharedRefs.nonEmpty) imports += s"""import type { ${sharedRefs.map(tsSafeIdent).mkString(", ")} } from "${up}common/types";"""

    val otherModuleRefs = referencedClasses
      .filter(c => !sharedClasses.contains(c) && primaryModule.get(c).exists(_ != moduleId))
      .flatMap(c => primaryModule.get(c).map(otherId => c -> otherId))
      .groupMap(_._2)(_._1)
    otherModuleRefs.toSeq.sortBy(_._1).foreach { case (otherId, cs) =>
      imports += s"""import type { ${cs.map(tsSafeIdent).sorted.mkString(", ")} } from "$up${folderById(otherId)}/types";"""
    }

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
      if (paramFields.isEmpty) "client: BaklavaClient"
      else s"client: BaklavaClient, params${if (paramsArgOptional) "?" else ""}: $paramsType"

    val urlExpr = renderUrlExpression(req, pathParams.map(_.name), queryParams.map(_.name))

    val bodyContentType = uniformBodyContentType(endpointCalls)
    val hasBody         = bodySchema.exists(!isEmptyBodyInstance(_))
    val isJsonBody      = hasBody && bodyContentType.forall(_.toLowerCase.contains("application/json"))

    val needsAuthHeaders = req.securitySchemes.exists { s =>
      val sec = s.security
      sec.httpBearer.isDefined || sec.httpBasic.isDefined ||
      sec.oAuth2InBearer.isDefined || sec.openIdConnectInBearer.isDefined
    }

    val headerLines = {
      val parts = new scala.collection.mutable.ListBuffer[String]
      if (needsAuthHeaders) parts += "    ...client.authHeaders(),"
      declaredHeaders.foreach { h =>
        val key  = h.name
        val cond =
          if (h.schema.required) s"""    "$key": String(${tsAccessor("params", h.name, optional = false)}),"""
          else
            s"""    ...(${tsAccessor("params", h.name, optional = true)} !== undefined ? { "$key": String(${tsAccessor(
                "params",
                h.name,
                optional = false
              )}) } : {}),"""
        parts += cond
      }
      if (isJsonBody) parts += """    "Content-Type": "application/json","""
      else bodyContentType.foreach(ct => parts += s"""    "Content-Type": "$ct",""")
      req.securitySchemes.foreach { scheme =>
        scheme.security.apiKeyInHeader.foreach { k =>
          parts += s"""    ...(client.apiKeys?.["${k.name}"] ? { "${k.name}": client.apiKeys["${k.name}"] } : {}),"""
        }
        scheme.security.apiKeyInCookie.foreach { k =>
          parts += s"""    ...(client.apiKeys?.["${k.name}"] ? { "Cookie": `${k.name}=$${client.apiKeys["${k.name}"]}` } : {}),"""
        }
      }
      parts.toList
    }

    val bodyLine =
      if (!hasBody) None
      else if (isJsonBody) Some("    body: JSON.stringify(params.body),")
      else Some("    body: params.body as unknown as BodyInit,")

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
          |  const ct = res.headers.get("content-type") ?? "";
          |  if (ct.includes("application/json")) {
          |    return (text ? JSON.parse(text) : undefined) as typeof __ret;
          |  }
          |  return text as unknown as typeof __ret;""".stripMargin

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
        schema.additionalPropertiesSchema.foreach(visit)
      case SchemaType.ObjectType =>
        schema.properties.values.foreach(visit)
        schema.additionalPropertiesSchema.foreach(visit)
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
        val refs = (schema.properties.values ++ schema.additionalPropertiesSchema).flatMap(directReferencesIn).toSet
        byClassName.update(schema.className, byClassName(schema.className) ++ refs)
        schema.properties.values.foreach(collectFromSchema)
        schema.additionalPropertiesSchema.foreach(collectFromSchema)
      case SchemaType.ObjectType =>
        schema.properties.values.foreach(collectFromSchema)
        schema.additionalPropertiesSchema.foreach(collectFromSchema)
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

  /** Named classes reachable from a schema's immediate property types. Named classes terminate descent (so the interface body can emit a
    * reference to them by name); anonymous object types get inlined, so we recurse through their properties to surface any named classes
    * they transitively embed.
    */
  private def directReferencesIn(schema: BaklavaSchemaSerializable): Set[String] = schema.`type` match {
    case SchemaType.ObjectType if isNamedInterface(schema) => Set(schema.className)
    case SchemaType.ObjectType => (schema.properties.values ++ schema.additionalPropertiesSchema).flatMap(directReferencesIn).toSet
    case SchemaType.ArrayType  => schema.items.toSet.flatMap(directReferencesIn)
    case _                     => Set.empty
  }

  private def collectUsageByModule(calls: Seq[BaklavaSerializableCall]): Map[String, Set[String]] = {
    val usage = scala.collection.mutable.Map.empty[String, Set[String]].withDefaultValue(Set.empty)
    calls.foreach { c =>
      val moduleId = moduleIdOf(c)
      val refs     = referencedClassesInCall(c)
      refs.foreach(cls => usage.update(cls, usage(cls) + moduleId))
    }
    // Also: if A is used by module X and A contains B, B is also (transitively) used by module X.
    var changed = true
    while (changed) {
      changed = false
      usage.toMap.foreach { case (cls, ids) =>
        directRefs.getOrElse(cls, Set.empty).foreach { child =>
          val next = usage(child) ++ ids
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
        if (acc.add(s.className)) {
          s.properties.values.foreach(visit)
          s.additionalPropertiesSchema.foreach(visit)
        }
      case SchemaType.ObjectType =>
        s.properties.values.foreach(visit)
        s.additionalPropertiesSchema.foreach(visit)
      case SchemaType.ArrayType => s.items.foreach(visit)
      case _                    => ()
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

  private def renderUrlExpression(
      req: BaklavaRequestContextSerializable,
      pathParamNames: Seq[String],
      queryParamNames: Seq[String]
  ): String = {
    val filled = pathParamNames.foldLeft(req.symbolicPath) { (acc, name) =>
      acc.replace(s"{$name}", s"$${encodeURIComponent(String(${tsAccessor("params", name, optional = false)}))}")
    }
    val urlLine    = s"""  const url = new URL(`$${client.baseUrl}$filled`);"""
    val queryLines = queryParamNames.map { name =>
      s"""  if (${tsAccessor("params", name, optional = true)} !== undefined) url.searchParams.set("$name", String(${tsAccessor(
          "params",
          name,
          optional = false
        )}));"""
    }
    val apiKeyQueryLines = req.securitySchemes.flatMap(_.security.apiKeyInQuery.toSeq).map { k =>
      s"""  if (client.apiKeys?.["${k.name}"]) url.searchParams.set("${k.name}", client.apiKeys["${k.name}"]);"""
    }
    (urlLine +: (queryLines ++ apiKeyQueryLines)).mkString("\n")
  }

  /** If every captured call on this endpoint declared the same non-empty request content-type, return it. */
  private def uniformBodyContentType(endpointCalls: Seq[BaklavaSerializableCall]): Option[String] = {
    val distinct = endpointCalls.flatMap(_.response.requestContentType).distinct
    if (distinct.size == 1) distinct.headOption else None
  }

  private def tsReturnType(endpointCalls: Seq[BaklavaSerializableCall]): String = {
    val successCalls = endpointCalls.filter(c => c.response.status.code >= 200 && c.response.status.code < 300)
    val picked       = if (successCalls.nonEmpty) successCalls else endpointCalls
    val rendered     = picked.flatMap(_.response.bodySchema).filterNot(isEmptyBodyInstance).map(tsType).distinct
    rendered match {
      case Nil        => "void"
      case one :: Nil => one
      case many       => many.mkString(" | ")
    }
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
      if (inner.contains(" | ") || inner.contains(" & ")) s"($inner)[]" else s"$inner[]"
    case SchemaType.ObjectType =>
      if (isNamedInterface(schema)) tsSafeIdent(schema.className)
      else
        schema.additionalPropertiesSchema match {
          case Some(v) => s"Record<string, ${tsType(v)}>"
          case None    => if (schema.properties.isEmpty) "Record<string, unknown>" else renderInterfaceBody(schema)
        }
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

  /** Emit `base.foo`/`base?.foo` for identifier-safe names, `base["X-Foo"]`/`base?.["X-Foo"]` otherwise. */
  private def tsAccessor(base: String, name: String, optional: Boolean): String = {
    val isIdent = name.matches("[A-Za-z_][A-Za-z0-9_]*")
    val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
    (optional, isIdent) match {
      case (false, true)  => s"$base.$name"
      case (false, false) => s"""$base["$escaped"]"""
      case (true, true)   => s"$base?.$name"
      case (true, false)  => s"""$base?.["$escaped"]"""
    }
  }

  private def write(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
}
