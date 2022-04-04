package pl.iterators.baklava.core.formatters

import com.github.andyglow.json.JsonFormatter
import com.github.andyglow.jsonschema.AsValue
import json.schema.Version.Draft07
import pl.iterators.baklava.core.model.EnrichedRouteRepresentation
import pl.iterators.baklava.core.utils.option.RichOptionCompanion

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Try

class SimpleOutputFormatter extends Formatter {

  override def generate(outputPath: String, routesList: List[EnrichedRouteRepresentation[_, _]]): Unit = {
    val dir = new File(outputPath)
    Try(dir.mkdirs())

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
      routesList
        .map { p =>
          val r = p.routeRepresentation

          val filename = r.name
            .replaceAll("/", "_")
            .replaceAll(" ", "_") + ".html"

          val fileWriter  = new FileWriter(s"$outputPath/$filename")
          val printWriter = new PrintWriter(fileWriter)

          printWriter.print(generateDoc(p))
          printWriter.close()
          fileWriter.close()

          s"""<tr><td style="width: 70px"><b><a href="$filename">${r.method}</b></td>
             |<td><a href="$filename">${r.path}</a></b></td></tr>""".stripMargin

        }
        .mkString("\n") + "</table>"

    val fileWriter  = new FileWriter(s"$outputPath/index.html")
    val printWriter = new PrintWriter(fileWriter)

    printWriter.print(indexContent)
    printWriter.close()
    fileWriter.close()

  }

  private def generateDoc(p: EnrichedRouteRepresentation[_, _]): String = {
    val r = p.routeRepresentation

    val statusCodes = p.enrichDescriptions
      .flatMap(_.statusCodeOpt)
      .distinct
      .sortBy(_.intValue)

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
        Some(s"<tr><td><b>METHOD</b></td><td>${r.method}</td></tr>"),
        Some(s"<tr><td><b>ROUTE</b></td><td>${r.path}</td></tr>"),
        Some(s"<tr><td><b>DESCRIPTION</b></td><td>${r.description}</td></tr>"),
        r.extendedDescription.map(s => s"<tr><td><b>EXTENDED DESCRIPTION</b></td><td>$s</td></tr>"),
        Option.when(r.authentication.nonEmpty) {
          s"<tr><td><b>AUTHENTICATION</b></td><td><ul>${r.authentication.map("<li>" ++ _.toString ++ "</li>").mkString}</ul></td></tr>"
        },
        Some(s"<tr><td><b>BEHAVIOUR</b></td><td>${p.enrichDescriptions
          .map { enrichedDescription =>
            s"${enrichedDescription.description} ${enrichedDescription.statusCodeOpt.map(c => s"-> [$c]").getOrElse("")}"
          }
          .mkString("<br/>")}</td></tr>"),
        Some(s"<tr><td><b>STATUS CODES</b></td><td>${statusCodes.mkString("<br/>")}</td></tr>"),
        Option.when(r.parameters.nonEmpty) {
          s"<tr><td><b>PARAMETERS</b></td><td>" +
            s"${r.parameters.map(h => s"${h.name} [${h.scalaType}] ${Option.when(h.required)(s"<b style='color: red'>*</b>").getOrElse("")}").mkString("<br/>")}" +
            s"</td></tr>" +
            s"<tr><td><b>ROUTE WITH MINIMAL PARAMS</b></td><td>${r.routePathWithRequiredParameters}</td></tr>" +
            s"<tr><td><b>ROUTE WITH ALL PARAMS</b></td><td>${r.routePathWithAllParameters}</td></tr>"
        },
        Option.when(r.headers.nonEmpty)(s"<tr><td><b>HEADERS</b></td><td>" +
          s"${r.headers.map(h => s"${h.name}${Option.when(h.required)("<b style='color: red'>*</b>").getOrElse("")}").mkString("<br/>")}" +
          s"</td></tr>"),
        r.request.scalaClassOpt.map(s => s"<tr><td><b>REQUEST SCALA TYPE</b></td><td><pre>$s</pre></td></tr>"),
        r.request.minimal.jsonString.map(s => s"<tr><td><b>REQUEST MINIMAL JSON</b></td><td><pre>$s</pre></td></tr>"),
        r.request.maximal.jsonString.map(s => s"<tr><td><b>REQUEST MAXIMAL JSON</b></td><td><pre>$s</pre></td></tr>"),
        r.request.schema.map(s => s"<tr><td><b>REQUEST SCHEMA</b></td><td><pre>${printSchema(s)}</pre></td></tr>"),
        r.response.scalaClassOpt.map(s => s"<tr><td><b>RESPONSE SCALA TYPE</b></td><td><pre>$s</pre></td></tr>"),
        r.response.minimal.jsonString.map(s => s"<tr><td><b>RESPONSE MINIMAL JSON</b></td><td><pre>$s</pre></td></tr>"),
        r.response.maximal.jsonString.map(s => s"<tr><td><b>RESPONSE MAXIMAL JSON</b></td><td><pre>$s</pre></td></tr>"),
        r.response.schema.map(s => s"<tr><td><b>RESPONSE SCHEMA</b></td><td><pre>${printSchema(s)}</pre></td></tr>"),
      ).flatten.mkString("\n") +
      "</table>"
  }

  private def printSchema[T](schema: json.Schema[T]): String =
    JsonFormatter.format(AsValue.schema(schema, Draft07("")))
}
