package pl.iterators.baklava

import io.circe.{Encoder, Json, Printer}
import io.circe.parser.*
import io.circe.syntax.EncoderOps

import java.io.{File, FileWriter, PrintWriter}

class BaklavaDslFormatterSimple extends BaklavaDslFormatter {

  private val dirName = "target/baklava/simple"
  private val dirFile = new File(dirName)

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    dirFile.mkdirs()

    val indexContent = """
                         |<style>
                         |table {
                         |  border: 1px solid #1C6EA4;
                         |  background-color: #EEEEEE;
                         |  text-align: left;
                         |  border-collapse: collapse;
                         |}
                         |table td, table th {
                         |  border: 1px solid #AAAAAA;
                         |  padding: 5px 5px 5px 5px;
                         |}
                         |table tbody td {
                         |  font-size: 16px;
                         |}
                         |table tr:nth-child(even) {
                         |  background: #D0E4F5;
                         |}
                         |a {
                         |  text-decoration: none;
                         |}
                         |a:link {
                         |  color: black;
                         |}
                         |a:visited {
                         |  color: black;
                         |}
                         |a:hover {
                         |  color: #444444;
                         |}
                         |a:active {
                         |  color: #888888;
                         |}
                         |</style>
                         |""".stripMargin +
      "<table>" +
      calls
        .groupBy(c => (c.request.method, c.request.symbolicPath))
        .toList
        .sortBy(s => (s._1._2, s._1._1.map(_.value).getOrElse("UNDEFINED")))
        .map { case ((method, symbolicPath), call) =>
          val methodName = method.map(_.value).getOrElse("UNDEFINED")
          val name       = s"$methodName $symbolicPath"

          val filename = name
            .replaceAll("/", "_")
            .replaceAll(" ", "_")
            .replaceAll("\\{", "__")
            .replaceAll("}", "__") + ".html"

          val fileWriter  = new FileWriter(s"$dirName/$filename")
          val printWriter = new PrintWriter(fileWriter)

          printWriter.print(generateForCall(call.sortBy(_.response.status.status)))
          printWriter.close()
          fileWriter.close()

          s"""<tr><td style="width: 70px"><b><a href="$filename">${methodName}</b></td>
             |<td><a href="$filename">${symbolicPath}</a></b></td></tr>""".stripMargin

        }
        .mkString("\n") + "</table>"

    val fileWriter  = new FileWriter(s"$dirName/index.html")
    val printWriter = new PrintWriter(fileWriter)

    printWriter.print(indexContent)
    printWriter.close()
    fileWriter.close()
  }

  private def generateForCall(calls: Seq[BaklavaSerializableCall]): String = {
    val request  = calls.head.request
    val response = calls.head.response

    """
      |<style>
      |table {
      |  border: 1px solid #1C6EA4;
      |  background-color: #EEEEEE;
      |  text-align: left;
      |  border-collapse: collapse;
      |}
      |table td, table th {
      |  border: 1px solid #AAAAAA;
      |  padding: 5px 5px 5px 5px;
      |}
      |table tbody td {
      |  font-size: 16px;
      |}
      |table tr:nth-child(even) {
      |  background: #D0E4F5;
      |}
      |</style>
      |""".stripMargin +
    "<table>" +
    List(
      Some(s"<tr><td><b>METHOD</b></td><td>${request.method.getOrElse("UNDEFINED")}</td></tr>"),
      Some(s"<tr><td><b>ROUTE</b></td><td>${request.symbolicPath}</td></tr>"),
      Some(s"<tr><td><b>SUMMARY</b></td><td>${request.operationSummary}</td></tr>"),
      Some(s"<tr><td><b>DESCRIPTION</b></td><td>${request.operationDescription}</td></tr>"),
//        r.extendedDescription.map(s => s"<tr><td><b>EXTENDED DESCRIPTION</b></td><td>$s</td></tr>"),
      Option.when(request.securitySchemes.nonEmpty) {
        s"<tr><td><b>AUTHENTICATION</b></td><td><ul>${request.securitySchemes.map(ss => "<li>" ++ ss.name ++ " " ++ ss.security.`type`.getOrElse("") ++ "</li>").mkString}</ul></td></tr>"
      },
//        Some(s"<tr><td><b>BEHAVIOUR</b></td><td>${p.enrichDescriptions
//          .map { enrichedDescription =>
//            s"${enrichedDescription.description} ${enrichedDescription.statusCodeOpt.map(c => s"-> [$c]").getOrElse("")}"
//          }
//          .mkString("<br/>")}</td></tr>"),
      Option.when(request.headersSeq.nonEmpty)(
        s"<tr><td><b>HEADERS</b></td><td>" +
          s"${request.headersSeq.map(h => s"${h.name}${Option.when(h.schema.required)("<b style='color: red'>*</b>").getOrElse("")}").mkString("<br/>")}" +
          s"</td></tr>"
      ),
      Option.when(request.pathParametersSeq.nonEmpty) {
        s"<tr><td><b>PATHS PARAMETERS</b></td><td>" +
        s"${request.pathParametersSeq
            .map(h =>
              s"${h.name}${if (h.schema.`type` == SchemaType.ArrayType) "[]" else ""}${Option.when(h.schema.required)(s"<b style='color: red'>*</b>").getOrElse("")}: ${h.schema.className}${
                  if (h.schema.`type` == SchemaType.ArrayType) "[]"
                  else ""
                } " +
              s"(${h.schema.`enum`
                  .map(enums => enums.mkString("possible values: ", ", ", ""))
                  .getOrElse("")})"
              // .getOrElse(s"example: ${h.valueGenerator()}")})"
            )
            .mkString("<br/>")}" +
        s"</td></tr>"
      },
      Option.when(request.queryParametersSeq.nonEmpty) {
        s"<tr><td><b>QUERY PARAMETERS</b></td><td>" +
        s"${request.pathParametersSeq
            .map(h =>
              s"${h.name}${if (h.schema.`type` == SchemaType.ArrayType) "[]" else ""}${Option.when(h.schema.required)(s"<b style='color: red'>*</b>").getOrElse("")}: ${h.schema.className}${
                  if (h.schema.`type` == SchemaType.ArrayType) "[]"
                  else ""
                } " +
              s"(${h.schema.`enum`
                  .map(enums => enums.mkString("possible values: ", ", ", ""))
                  .getOrElse("")})"
              // .getOrElse(s"example: ${h.valueGenerator()}")})"
            )
            .mkString("<br/>")}" +
        s"</td></tr>"
      },
      Some(s"<tr><td><b>STATUS CODES</b></td><td>${calls.map(_.response.status.status).mkString("<br/>")}</td></tr>"),
      Some(s"<tr><td><b>REQUEST BODY</b></td><td><pre>${jsonStr(response.requestBodyString)}</pre></td></tr>"),
      request.bodySchema.map(schema =>
        s"<tr><td><b>REQUEST BODY SCHEMA</b></td><td><pre>${baklavaSchemaToJsonSchemaV7(schema)}</pre></td></tr>"
      )
//todo
//s"<tr><td><b>ROUTE WITH MINIMAL PARAMS</b></td><td>${r.routePathWithRequiredParameters}</td></tr>" +//
//s"<tr><td><b>ROUTE WITH ALL PARAMS</b></td><td>${r.routePathWithAllParameters}</td></tr>"
    ).flatten
      .concat(calls.sortBy(_.response.status.status).flatMap { c =>
        List(
          Some(
            s"<tr><td><b>RESPONSE BODY ${c.response.status.status}</b></td><td><pre>${jsonStr(response.responseBodyString)}</pre></td></tr>"
          ),
          c.response.bodySchema
            .map(schema =>
              s"<tr><td><b>RESPONSE BODY SCHEMA ${c.response.status.status}</b></td><td><pre>${baklavaSchemaToJsonSchemaV7(schema)}</pre></td></tr>"
            )
        ).flatten
      })
      .mkString("\n") +
    "</table>"
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
        "properties" -> (if (baklavaSchema.`type` == SchemaType.ObjectType)
                           baklavaSchema.properties.view.mapValues(j => toJsonSchemaV7(j)).toMap.asJson
                         else Json.Null),
        "required" -> Json.arr(baklavaSchema.properties.collect {
          case (name, prop) if prop.required => Json.fromString(name)
        }.toSeq: _*),
        "additionalProperties" -> (if (baklavaSchema.`type` == SchemaType.ObjectType) Json.fromBoolean(baklavaSchema.additionalProperties)
                                   else Json.Null),
        "items" -> baklavaSchema.items.map(j => toJsonSchemaV7(j)).getOrElse(Json.Null)
      )
      .deepDropNullValues // Drop any null values from JSON for cleaner output
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
