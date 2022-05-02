package pl.iterators.baklava.formatteropenapi

import io.swagger.v3.core.util.Yaml
import pl.iterators.baklava.core.formatters.Formatter
import pl.iterators.baklava.core.model.EnrichedRouteRepresentation
import pl.iterators.baklava.formatteropenapi.utils._

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Try

class OpenApiFormatter extends Formatter {

  override def generate(outputBasePath: String, routesList: List[EnrichedRouteRepresentation[_, _]]): Unit = {
    val outputPath = s"$outputBasePath/openapi"
    val dir        = new File(outputPath)
    Try(dir.mkdirs())

    val fileWriter  = new FileWriter(s"$outputPath/openapi.yml")
    val printWriter = new PrintWriter(fileWriter)
    val output = new OpenApiFormatterWorker(new JsonSchemaToSwaggerSchemaWorker)
      .generateOpenApi(routesList)

    printWriter.print(Yaml.pretty().writeValueAsString(output))
    printWriter.close()
    fileWriter.close()
  }
}
