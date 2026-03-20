package pl.iterators.baklava.simple

import io.circe.{Encoder, Json, Printer}
import io.circe.parser.*
import io.circe.syntax.EncoderOps

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}

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
      |</style>""".stripMargin

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    dirFile.mkdirs()

    val endpoints = calls
      .groupBy(c => (c.request.method, c.request.symbolicPath))
      .toList
      .sortBy(s => (s._1._2, s._1._1.map(_.value).getOrElse("UNDEFINED")))

    val indexRows = endpoints.map { case ((method, symbolicPath), endpointCalls) =>
      val methodName = method.map(_.value).getOrElse("UNDEFINED")
      val filename   = toFilename(s"$methodName $symbolicPath")

      writeFile(s"$dirName/$filename", generateEndpointPage(endpointCalls.sortBy(_.response.status.status)))

      s"""<li><a href="$filename"><span class="method method-$methodName">$methodName</span> <span class="path">$symbolicPath</span></a></li>"""
    }

    val indexHtml =
      s"""<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>API Documentation</title>$css</head><body>
         |<h1>API Documentation</h1>
         |<ul class="endpoint-list">
         |${indexRows.mkString("\n")}
         |</ul>
         |</body></html>""".stripMargin

    writeFile(s"$dirName/index.html", indexHtml)
  }

  private def generateEndpointPage(calls: Seq[BaklavaSerializableCall]): String = {
    val request    = calls.head.request
    val methodName = request.method.map(_.value).getOrElse("UNDEFINED")

    val metaRows = List(
      request.operationSummary.map(s => metaRow("Summary", escHtml(s))),
      request.operationDescription.map(s => metaRow("Description", escHtml(s))),
      request.operationId.map(s => metaRow("Operation ID", s"""<code>$s</code>""")),
      Option.when(request.operationTags.nonEmpty)(
        metaRow("Tags", request.operationTags.map(t => s"""<span class="tag">$t</span>""").mkString(" "))
      )
    ).flatten

    val securitySection = Option.when(request.securitySchemes.nonEmpty) {
      card(
        "Security",
        request.securitySchemes
          .map(ss => s"<p>${escHtml(ss.name)} <span class=\"tag\">${ss.security.`type`.getOrElse("")}</span></p>")
          .mkString
      )
    }

    val headersSection = Option.when(request.headersSeq.nonEmpty) {
      card(
        "Headers",
        s"<dl class=\"meta-grid\">${request.headersSeq.map { h =>
            metaRow(h.name + (if (h.schema.required) " <span class=\"required\">*</span>" else ""), s"<code>${h.schema.className}</code>")
          }.mkString}</dl>"
      )
    }

    val pathParamsSection = Option.when(request.pathParametersSeq.nonEmpty) {
      card("Path Parameters", s"<dl class=\"meta-grid\">${request.pathParametersSeq.map(paramRow).mkString}</dl>")
    }

    val queryParamsSection = Option.when(request.queryParametersSeq.nonEmpty) {
      card("Query Parameters", s"<dl class=\"meta-grid\">${request.queryParametersSeq.map(paramRow).mkString}</dl>")
    }

    val requestBodySection = {
      val bodyJson = jsonStr(calls.head.response.requestBodyString)
      val parts    = List(
        Option.when(bodyJson.nonEmpty)(s"<pre>${escHtml(bodyJson)}</pre>"),
        request.bodySchema.map(schema =>
          s"<details><summary>Schema (JSON Schema v7)</summary><pre>${escHtml(baklavaSchemaToJsonSchemaV7(schema))}</pre></details>"
        )
      ).flatten
      Option.when(parts.nonEmpty)(card("Request Body", parts.mkString))
    }

    val responseSections = calls.sortBy(_.response.status.status).map { c =>
      val status    = c.response.status.status
      val statusCss = if (status < 300) "2xx" else if (status < 400) "3xx" else if (status < 500) "4xx" else "5xx"
      val desc      = c.request.responseDescription.map(d => s"<p>${escHtml(d)}</p>").getOrElse("")
      val bodyJson  = jsonStr(c.response.responseBodyString)
      val bodyPre   = Option.when(bodyJson.nonEmpty)(s"<pre>${escHtml(bodyJson)}</pre>")
      val schemaPre = c.response.bodySchema
        .map(schema => s"<details><summary>Schema</summary><pre>${escHtml(baklavaSchemaToJsonSchemaV7(schema))}</pre></details>")
      card(
        s"""<span class="status-badge status-$statusCss">$status</span> Response""",
        (List(Some(desc)) ++ List(bodyPre, schemaPre)).flatten.mkString
      )
    }

    s"""<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>$methodName ${request.symbolicPath}</title>$css</head><body>
       |<a href="index.html" class="back-link">&larr; Back to index</a>
       |<h1><span class="method method-$methodName">$methodName</span> <span class="path">${request.symbolicPath}</span></h1>
       |${if (metaRows.nonEmpty) card("Overview", s"<dl class=\"meta-grid\">${metaRows.mkString}</dl>") else ""}
       |${List(securitySection, headersSection, pathParamsSection, queryParamsSection, requestBodySection).flatten.mkString("\n")}
       |${responseSections.mkString("\n")}
       |</body></html>""".stripMargin
  }

  private def card(title: String, body: String): String =
    s"""<div class="card"><div class="card-header">$title</div><div class="card-body">$body</div></div>"""

  private def metaRow(label: String, value: String): String =
    s"<dt>$label</dt><dd>$value</dd>"

  private def paramRow(p: BaklavaPathParamSerializable): String = {
    val arrayFlag = if (p.schema.`type` == SchemaType.ArrayType) "[]" else ""
    val req       = if (p.schema.required) " <span class=\"required\">*</span>" else ""
    val enumInfo  = p.schema.`enum`.map(enums => s" <span class=\"tag\">${enums.mkString(" | ")}</span>").getOrElse("")
    metaRow(s"${p.name}$arrayFlag$req", s"<code>${p.schema.className}$arrayFlag</code>$enumInfo")
  }

  private def paramRow(p: BaklavaQueryParamSerializable): String = {
    val arrayFlag = if (p.schema.`type` == SchemaType.ArrayType) "[]" else ""
    val req       = if (p.schema.required) " <span class=\"required\">*</span>" else ""
    val enumInfo  = p.schema.`enum`.map(enums => s" <span class=\"tag\">${enums.mkString(" | ")}</span>").getOrElse("")
    metaRow(s"${p.name}$arrayFlag$req", s"<code>${p.schema.className}$arrayFlag</code>$enumInfo")
  }

  private def toFilename(name: String): String =
    name.replaceAll("/", "_").replaceAll(" ", "_").replaceAll("\\{", "__").replaceAll("}", "__") + ".html"

  private def escHtml(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  private def writeFile(path: String, content: String): Unit = {
    val fw = new FileWriter(path)
    val pw = new PrintWriter(fw)
    pw.print(content)
    pw.close()
    fw.close()
  }

  private def jsonStr(str: String): String =
    parse(str).map(_.printWith(Printer.spaces2)).getOrElse(str)

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
        "default"     -> baklavaSchema.default.asJson,
        "enum"        -> baklavaSchema.`enum`.map(_.toList.asJson).getOrElse(Json.Null),
        "properties"  -> (if (baklavaSchema.`type` == SchemaType.ObjectType)
                           baklavaSchema.properties.view.mapValues(j => toJsonSchemaV7(j)).toMap.asJson
                         else Json.Null),
        "required" -> Json.arr(baklavaSchema.properties.collect {
          case (name, prop) if prop.required => Json.fromString(name)
        }.toSeq: _*),
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
