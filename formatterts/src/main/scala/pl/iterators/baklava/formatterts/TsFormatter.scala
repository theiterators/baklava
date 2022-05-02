package pl.iterators.baklava.formatterts

import pl.iterators.baklava.core.formatters.Formatter
import pl.iterators.baklava.core.model.EnrichedRouteRepresentation
import pl.iterators.baklava.core.utils.option.RichOptionCompanion

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Try

trait TsFormatterBase extends Formatter {

  override def generate(outputBasePath: String, routesList: List[EnrichedRouteRepresentation[_, _]]): Unit = {
    val outputPath = s"$outputBasePath/ts"
    val dir        = new File(outputPath)
    Try(dir.mkdirs())

    val fileWriter  = new FileWriter(s"$outputPath/index.ts")
    val printWriter = new PrintWriter(fileWriter)

    printWriter.print(generateTs(routesList))
    printWriter.close()
    fileWriter.close()
  }

  private def generateTs(routesList: List[EnrichedRouteRepresentation[_, _]]): String = {
    val tsList = routesList.map(generateTs)

    s"""
       |import axios, {AxiosRequestConfig} from 'axios';
       |
       |${tsList.map(_._1).mkString("\n")}
       |
       |export function API(axios: any) {
       |  return {
       |${tsList.map(_._2).mkString(",\n")}
       |  }
       |}
       |""".stripMargin
  }

  private def generateTs(route: EnrichedRouteRepresentation[_, _]): (String, String) = {
    val pathParams  = generateRoutePathParamsInterface(route)
    val queryParams = generateQueryPathParamsInterface(route)
    val headers     = generateHeadersInterface(route)
    val request     = generateRequestInterface(route)
    val response    = generateResponseInterface(route)
    val configAndApi = generateAxiosConfigAndApi(route,
                                                 pathParams.map(_._1),
                                                 queryParams.map(_._1),
                                                 headers.map(_._1),
                                                 request.map(_._1),
                                                 response.map(_._1))

    (
      s"""/* -- Start of ${route.routeRepresentation.name} -- */ \n""" +
        pathParams.map(_._2).getOrElse("") +
        queryParams.map(_._2).getOrElse("") +
        headers.map(_._2).getOrElse("") +
        request.map(_._2).getOrElse("") +
        response.map(_._2).getOrElse("") +
        configAndApi._1 +
        s"""/* -- End of ${route.routeRepresentation.name} -- */ \n""",
      configAndApi._2
    )
  }

  private def generateRoutePathParamsInterface(route: EnrichedRouteRepresentation[_, _]): Option[(String, String)] = {
    val pattern = """\{(.*?)\}""".r
    val list = pattern
      .findAllMatchIn(route.routeRepresentation.path)
      .toList
      .map(_.group(1))
    Option.when(list.nonEmpty) {
      val name = s"${className(route)}PathParams"
      (
        name,
        list
          .map(i => s"""  \"$i\": string;""")
          .mkString(
            s"interface $name {\n",
            "\n",
            "\n}\n\n"
          )
      )
    }
  }

  private def generateQueryPathParamsInterface(route: EnrichedRouteRepresentation[_, _]): Option[(String, String)] = {
    Option.when(route.routeRepresentation.parameters.nonEmpty) {
      val name = s"${className(route)}QueryParams"
      (
        name,
        route.routeRepresentation.parameters
          .map { p =>
            s"""  \"${p.name}\"${Option
              .when(p.required)("")
              .getOrElse("?")}: string;"""
          }
          .mkString(
            s"interface $name {\n",
            "\n",
            "\n}\n\n"
          )
      )
    }
  }

  private def generateHeadersInterface(route: EnrichedRouteRepresentation[_, _]): Option[(String, String)] = {
    Option.when(route.routeRepresentation.headers.nonEmpty) {
      val name = s"${className(route)}Headers"
      (
        name,
        route.routeRepresentation.headers
          .map { p =>
            s"""  \"${p.name}\"${Option
              .when(p.required)("")
              .getOrElse("?")}: string;"""
          }
          .mkString(
            s"interface $name {\n",
            "\n",
            "\n}\n\n"
          )
      )
    }
  }

  private def generateRequestInterface(route: EnrichedRouteRepresentation[_, _]): Option[(String, String)] = {
    Option.when(!route.routeRepresentation.request.isUnit) {
      val name = s"${className(route)}Request"
      generateClassRepresentation(name, route.routeRepresentation.requestJsonSchemaWrapper.schema)
    }
  }

  private def generateResponseInterface(route: EnrichedRouteRepresentation[_, _]): Option[(String, String)] = {
    Option.when(!route.routeRepresentation.response.isUnit) {
      val name = s"${className(route)}Response"
      generateClassRepresentation(name, route.routeRepresentation.responseJsonSchemaWrapper.schema)
    }
  }

  private def generateAxiosConfigAndApi(
      route: EnrichedRouteRepresentation[_, _],
      pathParams: Option[String],
      queryParams: Option[String],
      headers: Option[String],
      request: Option[String],
      response: Option[String]
  ): (String, String) = {
    val allFunctionParamsWithNames = (
      pathParams.map(p => ("pathParams", p)) ::
        queryParams.map(p => ("queryParams", p)) ::
        headers.map(p => ("headers", p)) ::
        request.map(p => ("request", p)) ::
        Nil
    )

    val functionParamsNamesAndValuesString = allFunctionParamsWithNames.flatten
      .map(p => s"${p._1}: ${p._2}")
      .mkString(", ")

    val functionParamsNamesString =
      allFunctionParamsWithNames.flatten.map(_._1).mkString(", ")

    val pathPattern = """\{(.*?)\}""".r

    val path = pathPattern.replaceAllIn(route.routeRepresentation.path, m => "\\${pathParams." + m.group(1) + "}")

    val description = route.routeRepresentation.description +
      "\n" +
      route.enrichDescriptions
        .map { enrichedDescription =>
          s"      ${enrichedDescription.description} ${enrichedDescription.statusCodeOpt
            .map(c => s"-> [$c]")
            .getOrElse("")}"
        }
        .mkString("\n")

    (
      s"""function ${methodName(route)}AxiosConfig($functionParamsNamesAndValuesString): AxiosRequestConfig {\n""" +
        s"""  return {\n""" +
        queryParams.map(_ => "    params: queryParams,\n").getOrElse("") +
        headers.map(_ => "    headers: headers,\n").getOrElse("") +
        request.map(_ => "    data: request,\n").getOrElse("") +
        s"""    url: `$path`,\n""" +
        s"""    method: "${route.routeRepresentation.method.toLowerCase}"\n""" +
        s"""  }\n""" +
        s"""}\n""",
      s"""
         |    /*
         |      $description
         |    */
         |    ${methodName(route)}: function($functionParamsNamesAndValuesString): Promise<${response
           .getOrElse("{}")}> {
         |      return axios(${methodName(route)}AxiosConfig($functionParamsNamesString))
         |        .then(function (response: any) {
         |           return response.data;
         |        });
         |    }""".stripMargin
    )
  }

  private def generateClassRepresentation(name: String, jsonSchema: json.Schema[_]): (String, String) = {
    jsonSchema.jsonType match {
      case "boolean" =>
        ("boolean", "")
      case "number" =>
        ("number", "")
      case "integer" =>
        ("number", "")
      case "string" =>
        ("string", "")
      case "enum" =>
        ("string", "")
      case "array" =>
        val s = jsonSchema.asInstanceOf[json.Schema.array[AnyRef, json.Schema]]
        val (in, after) =
          generateClassRepresentation(s"${name}Item", s.componentType)

        (s"$in[]", after)

      case "object" =>
        if (jsonSchema.toString.startsWith("dictionary")) {
          val s =
            jsonSchema.asInstanceOf[json.Schema.dictionary[AnyRef, AnyRef, Map]]
          val (in, after) =
            generateClassRepresentation(s"${name}Value", s.valueType)

          (
            name,
            s"""interface $name {
               |  [key: string]: $in;
               |}
               |""".stripMargin + after
          )
        } else {
          val s = jsonSchema.asInstanceOf[json.Schema.`object`[AnyRef]]
          val fields = s.fields.map { f =>
            val newName = s"${name}${f.name.capitalize}"
            (f.name, generateClassRepresentation(newName, f.tpe))
          }

          (
            name,
            s"""interface $name {
               |${fields
                 .map(f => s"""  "${f._1}": ${f._2._1};""")
                 .mkString("\n")}
               |}
               |""".stripMargin + fields.map(_._2._2).mkString("\n")
          )
        }
      case _ =>
        ("", "")
    }
  }

  private def className(route: EnrichedRouteRepresentation[_, _]): String = {
    val methodPart = route.routeRepresentation.method.toLowerCase.capitalize
    s"${methodPart}${pathName(route.routeRepresentation.path)}"
  }

  private def methodName(route: EnrichedRouteRepresentation[_, _]): String = {
    val methodPart = route.routeRepresentation.method.toLowerCase
    s"${methodPart}${pathName(route.routeRepresentation.path)}"
  }

  protected def pathName(path: String): String
}

class TsFormatter extends TsFormatterBase {
  override protected def pathName(path: String): String = {
    path
      .replaceAll("\\{", "")
      .replaceAll("\\}", "")
      .split("/")
      .map(_.toLowerCase.capitalize)
      .mkString("")
  }
}

class TsStrictFormatter extends TsFormatterBase {
  override protected def pathName(path: String): String = {
    val pathParts = path.split("/").map(p => (p, p.startsWith("{"))).tail
    pathParts.indices
      .map { idx =>
        val (name, isParam) = pathParts(idx)
        val prev            = pathParts.lift(idx - 1)
        val next            = pathParts.lift(idx + 1)
        if (!isParam && next.forall(_._2 == false)) {
          name
        } else if (!isParam && next.exists(_._2 == true)) {
          pluralToSingular(name)
        } else if (prev.isEmpty) {
          name.replaceAll("\\{", "").replaceAll("\\}", "")
        } else {
          ""
        }
      }
      .map(_.capitalize)
      .mkString("")
  }

  private def pluralToSingular(word: String): String = {
    if (word.endsWith("es")) {
      word.stripSuffix("es")
    } else if (word.endsWith("s")) {
      word.stripSuffix("s")
    } else {
      word
    }
  }
}
