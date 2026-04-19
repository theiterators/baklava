package pl.iterators.baklava.simple

import io.circe.{Encoder, Json, Printer}
import io.circe.parser.*
import io.circe.syntax.EncoderOps

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

class BaklavaDslFormatterSimple extends BaklavaDslFormatter {

  private val dirName = "target/baklava/simple"
  private val dirFile = new File(dirName)

  private val css =
    """<style>
      |  *, *::before, *::after { box-sizing: border-box; }
      |  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px 40px; background: #f8f9fa; color: #1a1a2e; line-height: 1.5; }
      |  h1 { font-size: 1.6rem; font-weight: 600; margin: 0 0 20px; color: #16213e; }
      |  a { color: #0d6efd; text-decoration: none; }
      |  a:hover { text-decoration: underline; }
      |  .endpoint-list { list-style: none; padding: 0; margin: 0; }
      |  .endpoint-list li { background: #fff; border: 1px solid #dee2e6; border-radius: 6px; margin-bottom: 8px; padding: 10px 16px; display: flex; align-items: center; gap: 12px; }
      |  .endpoint-list li:hover { border-color: #0d6efd; }
      |  .method { display: inline-block; font-size: 0.75rem; font-weight: 700; padding: 2px 8px; border-radius: 4px; min-width: 56px; text-align: center; color: #fff; }
      |  .method-GET { background: #198754; } .method-POST { background: #0d6efd; } .method-PUT { background: #fd7e14; }
      |  .method-PATCH { background: #6f42c1; } .method-DELETE { background: #dc3545; } .method-HEAD { background: #6c757d; }
      |  .method-OPTIONS { background: #20c997; } .method-TRACE { background: #6c757d; } .method-CONNECT { background: #6c757d; }
      |  .path { font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace; font-size: 0.9rem; }
      |  .card { background: #fff; border: 1px solid #dee2e6; border-radius: 8px; margin-bottom: 16px; overflow: hidden; }
      |  .card-header { padding: 12px 16px; font-weight: 600; font-size: 0.85rem; color: #495057; background: #f1f3f5; border-bottom: 1px solid #dee2e6; text-transform: uppercase; letter-spacing: 0.05em; }
      |  .card-body { padding: 16px; }
      |  .card-body p { margin: 0 0 8px; }
      |  .meta-grid { display: grid; grid-template-columns: 140px 1fr; gap: 0; }
      |  .meta-grid dt { padding: 8px 12px; font-weight: 600; font-size: 0.8rem; color: #495057; background: #f8f9fa; border-bottom: 1px solid #eee; }
      |  .meta-grid dd { padding: 8px 12px; margin: 0; border-bottom: 1px solid #eee; font-size: 0.9rem; }
      |  .tag { display: inline-block; background: #e9ecef; color: #495057; padding: 1px 8px; border-radius: 4px; font-size: 0.8rem; margin-right: 4px; }
      |  .required { color: #dc3545; font-weight: 700; }
      |  pre { background: #212529; color: #e9ecef; padding: 14px; border-radius: 6px; overflow-x: auto; font-size: 0.82rem; line-height: 1.5; margin: 0; }
      |  .status-badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-weight: 600; font-size: 0.8rem; color: #fff; }
      |  .status-2xx { background: #198754; } .status-3xx { background: #0dcaf0; color: #000; } .status-4xx { background: #fd7e14; } .status-5xx { background: #dc3545; }
      |  .back-link { display: inline-block; margin-bottom: 16px; font-size: 0.85rem; }
      |  ul.examples { list-style: none; padding: 0; margin: 4px 0 0; font-size: 0.85rem; }
      |  ul.examples li { padding: 2px 0; }
      |  .tag-group { margin-bottom: 24px; }
      |  .tag-heading { margin: 20px 0 10px; font-size: 1.1rem; font-weight: 600; color: #16213e; text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 2px solid #dee2e6; padding-bottom: 6px; }
      |  .curl-block { position: relative; margin: 0 0 8px; }
      |  .curl-block pre { padding-right: 72px; }
      |  .copy-btn { position: absolute; top: 8px; right: 8px; background: #495057; color: #fff; border: none; border-radius: 4px; padding: 4px 10px; font-size: 0.75rem; cursor: pointer; font-family: inherit; }
      |  .copy-btn:hover { background: #0d6efd; }
      |  .copy-btn:active { background: #0a58ca; }
      |</style>""".stripMargin

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    dirFile.mkdirs()

    val endpoints = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .sortBy(s => (s._1._2, s._1._1.map(_.method).getOrElse("UNDEFINED")))

    // (method, symbolicPath, filename, tags) — write each endpoint page up-front and remember the
    // tags so we can group them on the index like Swagger UI does.
    val rendered = endpoints.map { case ((method, symbolicPath), endpointCalls) =>
      val methodName = method.map(_.method).getOrElse("UNDEFINED")
      val filename   = toFilename(s"$methodName $symbolicPath")
      writeFile(s"$dirName/$filename", generateEndpointPage(endpointCalls.sortBy(_.response.status.code)))
      val tags = endpointCalls.flatMap(_.request.operationTags).distinct
      (methodName, symbolicPath, filename, tags)
    }

    writeFile(s"$dirName/index.html", renderIndexHtml(rendered))
  }

  /** Build the index HTML that groups every endpoint by its tag, Swagger-UI-style. Each operation appears under *every* tag it declares.
    * Operations with no tags go under a `default` section. Tag sections are alphabetized; `default`, when present, is pushed to the end.
    *
    * `rendered` carries `(methodName, symbolicPath, filename, tags)` for every endpoint page.
    */
  private[simple] def renderIndexHtml(rendered: Seq[(String, String, String, Seq[String])]): String = {
    val DefaultTag = "default"
    val byTag      = rendered.flatMap { case e @ (_, _, _, tags) =>
      val owners = if (tags.isEmpty) Seq(DefaultTag) else tags
      owners.map(t => t -> e)
    }
    val tagSections = byTag
      .groupMap(_._1)(_._2)
      .toList
      .sortBy { case (tag, _) => if (tag == DefaultTag) (1, tag) else (0, tag) }
      .map { case (tag, entries) =>
        val rows = entries
          .sortBy { case (method, path, _, _) => (path, method) }
          .map { case (method, path, filename, _) =>
            s"""<li><a href="${escHtmlAttr(filename)}"><span class="method method-${escHtmlAttr(method)}">${escHtml(
                method
              )}</span> <span class="path">${escHtml(path)}</span></a></li>"""
          }
          .mkString("\n")
        s"""<section class="tag-group"><h2 class="tag-heading">${escHtml(tag)}</h2><ul class="endpoint-list">$rows</ul></section>"""
      }

    s"""<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>API Documentation</title>$css</head><body>
       |<h1>API Documentation</h1>
       |${tagSections.mkString("\n")}
       |</body></html>""".stripMargin
  }

  private[simple] def generateEndpointPage(calls: Seq[BaklavaSerializableCall]): String = {
    require(calls.nonEmpty, "generateEndpointPage called with empty calls")
    val request    = calls.head.request
    val methodName = request.method.map(_.method).getOrElse("UNDEFINED")

    val metaRows = List(
      request.operationSummary.map(s => metaRow("Summary", escHtml(s))),
      request.operationDescription.map(s => metaRow("Description", escHtml(s))),
      request.operationId.map(s => metaRow("Operation ID", s"""<code>${escHtml(s)}</code>""")),
      Option.when(request.operationTags.nonEmpty)(
        metaRow("Tags", request.operationTags.map(t => s"""<span class="tag">${escHtml(t)}</span>""").mkString(" "))
      )
    ).flatten

    val securitySection = Option.when(request.securitySchemes.nonEmpty) {
      card(
        "Security",
        request.securitySchemes
          .map(ss => s"<p>${escHtml(ss.name)} <span class=\"tag\">${escHtml(ss.security.`type`.getOrElse(""))}</span></p>")
          .mkString
      )
    }

    // Collect captured `(scenarioName, exampleValue)` pairs for each named parameter across every
    // call, so when values differ between scenarios we can show the reader all of them inline (the
    // OpenAPI generator already does the same for `parameter.examples`).
    def collectExamples(extract: BaklavaRequestContextSerializable => Seq[(String, Option[String])]): Map[String, Seq[(String, String)]] =
      calls
        .flatMap { c =>
          val label = c.request.responseDescription.getOrElse("")
          extract(c.request).collect { case (name, Some(value)) => name -> (label -> value) }
        }
        .groupMap(_._1)(_._2)

    val headerExamples = collectExamples(r => r.headersSeq.map(h => h.name -> h.example))
    val pathExamples   = collectExamples(r => r.pathParametersSeq.map(p => p.name -> p.example))
    val queryExamples  = collectExamples(r => r.queryParametersSeq.map(p => p.name -> p.example))

    val headersSection = Option.when(request.headersSeq.nonEmpty) {
      card(
        "Headers",
        s"<dl class=\"meta-grid\">${request.headersSeq.map(h => paramRow(h.name, h.schema, headerExamples.getOrElse(h.name, Nil))).mkString}</dl>"
      )
    }

    val pathParamsSection = Option.when(request.pathParametersSeq.nonEmpty) {
      card(
        "Path Parameters",
        s"<dl class=\"meta-grid\">${request.pathParametersSeq.map(p => paramRow(p.name, p.schema, pathExamples.getOrElse(p.name, Nil))).mkString}</dl>"
      )
    }

    val queryParamsSection = Option.when(request.queryParametersSeq.nonEmpty) {
      card(
        "Query Parameters",
        s"<dl class=\"meta-grid\">${request.queryParametersSeq.map(p => paramRow(p.name, p.schema, queryExamples.getOrElse(p.name, Nil))).mkString}</dl>"
      )
    }

    // Shared request-schema block (same for every call on this endpoint).
    val requestSchemaBlock =
      request.bodySchema.map(schema =>
        s"<details><summary>Request schema (JSON Schema v7)</summary><pre>${escHtml(baklavaSchemaToJsonSchemaV7(schema))}</pre></details>"
      )

    val responseSections = calls.sortBy(c => (c.response.status.code, c.request.responseDescription.getOrElse(""))).map { c =>
      val status    = c.response.status.code
      val statusCss = if (status < 300) "2xx" else if (status < 400) "3xx" else if (status < 500) "4xx" else "5xx"
      val desc      = c.request.responseDescription.map(d => s"<p>${escHtml(d)}</p>").getOrElse("")

      // Per-call curl command with a copy-to-clipboard button. The actual Authorization token /
      // API key isn't in the serialized call (only the scheme type is), so the curl uses
      // placeholders the caller must fill in — same compromise the OpenAPI `securitySchemes`
      // render makes.
      val curlBlock = renderCurl(c)

      // Per-call request body — previously only calls.head was rendered, so distinct inputs
      // across calls were silently dropped. Now each call's request body shows alongside its
      // response.
      val requestBodyJson = jsonStr(c.response.requestBodyString)
      val requestBodyPre  = Option
        .when(requestBodyJson.nonEmpty)(s"<h4>Request body</h4><pre>${escHtml(requestBodyJson)}</pre>")

      val responseBodyJson = jsonStr(c.response.responseBodyString)
      val responseBodyPre  = Option
        .when(responseBodyJson.nonEmpty)(s"<h4>Response body</h4><pre>${escHtml(responseBodyJson)}</pre>")
      val schemaPre = c.response.bodySchema
        .map(schema => s"<details><summary>Response schema</summary><pre>${escHtml(baklavaSchemaToJsonSchemaV7(schema))}</pre></details>")

      // Declared response headers (from the DSL) with their captured example values.
      val responseHeadersSection = Option.when(c.request.responseHeaders.nonEmpty) {
        val rows = c.request.responseHeaders.sortBy(_.name).map { h =>
          val example = c.response.headers
            .find(_.name.toLowerCase == h.name.toLowerCase)
            .map(sent => s" = <code>${escHtml(sent.value)}</code>")
            .getOrElse("")
          metaRow(
            escHtml(h.name) + (if (h.schema.required) " <span class=\"required\">*</span>" else ""),
            s"<code>${escHtml(h.schema.className)}</code>$example"
          )
        }
        s"<h4>Response headers</h4><dl class=\"meta-grid\">${rows.mkString}</dl>"
      }

      card(
        s"""<span class="status-badge status-$statusCss">$status</span> Response""",
        (List(Some(desc), Some(curlBlock)) ++ List(responseHeadersSection, requestBodyPre, responseBodyPre, schemaPre)).flatten.mkString
      )
    }

    val requestBodySection = requestSchemaBlock.map(s => card("Request body", s))

    s"""<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>${escHtml(
        methodName
      )} ${escHtml(request.symbolicPath)}</title>$css</head><body>
       |<a href="index.html" class="back-link">&larr; Back to index</a>
       |<h1><span class="method method-${escHtmlAttr(methodName)}">${escHtml(methodName)}</span> <span class="path">${escHtml(
        request.symbolicPath
      )}</span></h1>
       |${if (metaRows.nonEmpty) card("Overview", s"<dl class=\"meta-grid\">${metaRows.mkString}</dl>") else ""}
       |${List(securitySection, headersSection, pathParamsSection, queryParamsSection, requestBodySection).flatten.mkString("\n")}
       |${responseSections.mkString("\n")}
       |$copyScript
       |</body></html>""".stripMargin
  }

  // One inline script for the whole page. Binds a click handler to every `button.copy-btn`,
  // reads the payload from the sibling `<pre>` (so line breaks survive — attribute values have
  // their whitespace normalized by the HTML parser), tries `navigator.clipboard.writeText` first,
  // and falls back to `document.execCommand('copy')` on insecure/file contexts where the Clipboard
  // API is unavailable. Feedback is shown via the button text ("Copied" / "Failed").
  private val copyScript =
    """<script>
      |document.addEventListener('click', function (e) {
      |  var btn = e.target.closest('button.copy-btn');
      |  if (!btn) return;
      |  var pre = btn.previousElementSibling;
      |  var payload = pre ? pre.textContent : '';
      |  var flash = function (msg) {
      |    var original = btn.dataset.origText || btn.textContent;
      |    btn.dataset.origText = original;
      |    btn.textContent = msg;
      |    setTimeout(function () { btn.textContent = original; }, 1200);
      |  };
      |  var fallback = function () {
      |    try {
      |      var ta = document.createElement('textarea');
      |      ta.value = payload;
      |      ta.style.position = 'fixed';
      |      ta.style.opacity = '0';
      |      document.body.appendChild(ta);
      |      ta.select();
      |      var ok = document.execCommand('copy');
      |      document.body.removeChild(ta);
      |      flash(ok ? 'Copied' : 'Failed');
      |    } catch (_) { flash('Failed'); }
      |  };
      |  if (navigator.clipboard && navigator.clipboard.writeText) {
      |    navigator.clipboard.writeText(payload).then(function () { flash('Copied'); }, fallback);
      |  } else {
      |    fallback();
      |  }
      |});
      |</script>""".stripMargin

  /** Render a per-call curl command + a copy-to-clipboard button.
    *
    * The captured call gives us: method, resolved path, request-body string, request content-type, declared security schemes (as types,
    * without the live token) and declared headers (with the value the test sent, if any). For security we emit placeholder headers the
    * reader fills in — the actual token/secret/key isn't serialized.
    */
  private[simple] def renderCurl(c: BaklavaSerializableCall): String = {
    val method = c.request.method.map(_.method).getOrElse("GET")

    val authHeaders: Seq[(String, String)] = c.request.securitySchemes.flatMap { scheme =>
      val s = scheme.security
      s.httpBearer.map(_ => "Authorization" -> "Bearer <TOKEN>") orElse
      s.httpBasic.map(_ => "Authorization" -> "Basic <BASE64(USER:PASSWORD)>") orElse
      s.apiKeyInHeader.map(k => k.name -> "<API_KEY>") orElse
      s.apiKeyInCookie.map(k => "Cookie" -> s"${k.name}=<API_KEY>") orElse
      s.openIdConnectInBearer.map(_ => "Authorization" -> "Bearer <OIDC_TOKEN>") orElse
      s.oAuth2InBearer.map(_ => "Authorization" -> "Bearer <OAUTH_TOKEN>")
    }

    // Never leak captured values into headers that the security-scheme placeholders cover. That
    // means `Authorization`, `Cookie`, and any `<name>` declared via ApiKeyInHeader — if the user
    // also declared one of these via `headers = h[String]("Authorization")` the captured value
    // would otherwise end up in the generated curl verbatim.
    val redactedNames: Set[String] = {
      val base        = Set("authorization", "cookie")
      val apiKeyNames = c.request.securitySchemes.flatMap(_.security.apiKeyInHeader.map(_.name.toLowerCase(java.util.Locale.ROOT)))
      base ++ apiKeyNames
    }
    val declaredHeaders: Seq[(String, String)] =
      c.request.headersSeq
        .flatMap(h => h.example.map(v => h.name -> v))
        .filterNot { case (name, _) => redactedNames.contains(name.toLowerCase(java.util.Locale.ROOT)) }

    val contentTypeHeader: Seq[(String, String)] =
      c.response.requestContentType.map("Content-Type" -> _).toSeq

    // Auth placeholders come FIRST in the merge so `distinctBy` keeps them and discards any
    // accidentally-declared duplicate of `Authorization` / `Cookie` / an API-key header.
    val allHeaders = (contentTypeHeader ++ authHeaders ++ declaredHeaders).distinctBy(_._1.toLowerCase(java.util.Locale.ROOT))

    val headerLines = allHeaders.map { case (k, v) => s"  -H ${shellSingleQuote(s"$k: $v")} \\\n" }

    val bodyLine = if (c.response.requestBodyString.nonEmpty) s"  --data-raw ${shellSingleQuote(c.response.requestBodyString)}\n" else ""

    val cmd =
      s"curl -X $method ${shellSingleQuote(s"$$BASE_URL${c.request.path}")} \\\n" +
        headerLines.mkString +
        bodyLine

    // Trim the trailing ` \` off the last continuation so the command pastes cleanly.
    val trimmed = cmd.stripSuffix(" \\\n").stripSuffix("\n")

    // Copy the actual newlines from the adjacent <pre> rather than storing the payload in a
    // `data-*` attribute. Attribute values have their whitespace normalized during HTML parsing,
    // so the captured newlines/`\` continuations wouldn't survive round-trip through
    // `.getAttribute(...)`. Reading `previousElementSibling.textContent` returns exactly what the
    // user sees.
    s"""<h4>Curl</h4><div class="curl-block"><pre>${escHtml(
        trimmed
      )}</pre><button class="copy-btn" type="button">Copy</button></div>"""
  }

  /** Wrap `s` in single quotes for safe shell consumption. Single quotes can't be escaped inside a single-quoted string, so any literal
    * `'` is emitted as `'\''` (close quote, escaped quote, reopen quote). All other characters pass through unchanged.
    */
  private def shellSingleQuote(s: String): String =
    "'" + s.replace("'", "'\\''") + "'"

  private def card(title: String, body: String): String =
    s"""<div class="card"><div class="card-header">$title</div><div class="card-body">$body</div></div>"""

  private def metaRow(label: String, value: String): String =
    s"<dt>$label</dt><dd>$value</dd>"

  /** Render one parameter row.
    *
    * `examples` is a list of `(scenarioLabel, exampleValue)` pairs captured across all calls for the named parameter. When all calls
    * captured the same value we print a single inline `= value`; when they diverge we print a small list so the reader sees each
    * scenario's value. An empty list produces just the type (no examples captured).
    */
  private def paramRow(name: String, schema: BaklavaSchemaSerializable, examples: Seq[(String, String)]): String = {
    val arrayFlag = if (schema.`type` == SchemaType.ArrayType) "[]" else ""
    val req       = if (schema.required) " <span class=\"required\">*</span>" else ""
    val enumInfo  =
      schema.`enum`.map(enums => s""" <span class="tag">${escHtml(enums.toSeq.sorted.mkString(" | "))}</span>""").getOrElse("")

    val distinctValues = examples.map(_._2).distinct
    val exampleRender  =
      if (distinctValues.isEmpty) ""
      else if (distinctValues.size == 1) s" = <code>${escHtml(distinctValues.head)}</code>"
      else {
        val rows = examples.zipWithIndex.map { case ((label, value), idx) =>
          val key = if (label.isEmpty) s"Example ${idx + 1}" else label
          s"<li><em>${escHtml(key)}:</em> <code>${escHtml(value)}</code></li>"
        }
        s"""<ul class="examples">${rows.mkString}</ul>"""
      }

    metaRow(s"${escHtml(name)}$arrayFlag$req", s"<code>${escHtml(schema.className)}$arrayFlag</code>$enumInfo$exampleRender")
  }

  /** Collision-resistant filename from a (method + path) combination. The slash/space/brace substitutions are lossy, so we append a
    * deterministic 32-bit hex hash of the original input to distinguish otherwise-equivalent names. Common collisions (`/a/b` vs `/a_b`,
    * `/x {y}` vs `/x__y__`) no longer overwrite each other. A truly hostile caller could still craft a hashCode collision, but in practice
    * this is impossible to hit accidentally.
    */
  private[simple] def toFilename(name: String): String = {
    val cleaned = name.replaceAll("/", "_").replaceAll(" ", "_").replaceAll("\\{", "__").replaceAll("}", "__")
    f"$cleaned-${name.hashCode & 0xffffffffL}%08x.html"
  }

  private[simple] def jsonSchemaV7(baklavaSchema: BaklavaSchemaSerializable): String = baklavaSchemaToJsonSchemaV7(baklavaSchema)

  private def escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  private def escHtmlAttr(s: String): String =
    escHtml(s).replace("\"", "&quot;").replace("'", "&#39;")

  private def writeFile(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.print(content))

  private val maxRawFallbackChars = 8 * 1024

  /** Pretty-prints `str` as JSON when it parses. Otherwise returns the raw string, truncated to maxRawFallbackChars so a multi-megabyte
    * minified payload doesn't blow up the rendered HTML. All callers pass the result through escHtml, so no XSS risk from the fallback
    * path.
    */
  private def jsonStr(str: String): String =
    parse(str)
      .map(_.printWith(Printer.spaces2))
      .getOrElse(if (str.length > maxRawFallbackChars) str.take(maxRawFallbackChars) + "\n... [truncated]" else str)

  private def baklavaSchemaToJsonSchemaV7(baklavaSchema: BaklavaSchemaSerializable): String = {
    val jsonSchema = toJsonSchemaV7(baklavaSchema, true)
    val printer    = Printer.spaces2
    printer.print(jsonSchema)
  }

  private def toJsonSchemaV7(baklavaSchema: BaklavaSchemaSerializable, root: Boolean = false): Json = {
    Json
      .obj(
        "$schema"     -> (if (root) Json.fromString("http://json-schema.org/draft-07/schema#") else Json.Null),
        "title"       -> (if (baklavaSchema.`type` == SchemaType.ObjectType) Json.fromString(baklavaSchema.className) else Json.Null),
        "type"        -> baklavaSchema.`type`.asJson,
        "format"      -> baklavaSchema.format.asJson,
        "description" -> baklavaSchema.description.asJson,
        // `default` now arrives as structured JSON (issue #61) — inline it directly.
        "default"    -> baklavaSchema.default.getOrElse(Json.Null),
        "enum"       -> baklavaSchema.`enum`.map(_.toList.asJson).getOrElse(Json.Null),
        "properties" -> (if (baklavaSchema.`type` == SchemaType.ObjectType)
                           baklavaSchema.properties.view.mapValues(j => toJsonSchemaV7(j)).toMap.asJson
                         else Json.Null),
        "required" -> (if (baklavaSchema.`type` == SchemaType.ObjectType)
                         Json.arr(
                           baklavaSchema.properties.toSeq
                             .collect {
                               case (name, prop) if prop.required => name
                             }
                             .sorted
                             .map(Json.fromString): _*
                         )
                       else Json.Null),
        "additionalProperties" -> (if (baklavaSchema.`type` == SchemaType.ObjectType) Json.fromBoolean(baklavaSchema.additionalProperties)
                                   else Json.Null),
        "items" -> baklavaSchema.items.map(j => toJsonSchemaV7(j)).getOrElse(Json.Null)
      )
      .deepDropNullValues
  }

  private implicit val encodeSchemaType: Encoder[SchemaType] = Encoder.encodeString.contramap {
    case SchemaType.NullType    => "null"
    case SchemaType.StringType  => "string"
    case SchemaType.BooleanType => "boolean"
    case SchemaType.IntegerType => "integer"
    case SchemaType.NumberType  => "number"
    case SchemaType.ArrayType   => "array"
    case SchemaType.ObjectType  => "object"
  }

}
