package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import pl.iterators.baklava.*

import java.io.{File, FileOutputStream}
import scala.jdk.CollectionConverters.*
import scala.util.Using

class BaklavaDslFormatterOpenAPI extends BaklavaDslFormatter {

  private val dirName = "target/baklava/openapi"
  private val dirFile = new File(dirName)

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    dirFile.mkdirs()
    val openapiFile = new File(s"$dirName/openapi.yml")

    Using(new FileOutputStream(openapiFile)) { outputStream =>
      val openAPI = config
        .get("openapi-info")
        .map(parseOpenApiInfo)
        .getOrElse(new OpenAPI())

      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, calls)

      BaklavaOpenApiPostProcessor.postProcessorsFor(config).foreach(_.process(openAPI))

      val ymlString = Yaml.pretty(openAPI)

      outputStream.write(ymlString.getBytes)

    }.recover { case e: Exception =>
      System.err.println(s"Baklava: failed to write OpenAPI output: $e")
    }
    ()
  }

  private def parseOpenApiInfo(raw: String): OpenAPI = {
    val parser = new OpenAPIV3Parser
    val result = parser.readContents(raw, null, null)

    val messages = Option(result).toSeq.flatMap(r => Option(r.getMessages).toSeq.flatMap(_.asScala))
    messages.foreach(msg => System.err.println(s"Baklava: openapi-info parse message: $msg"))

    Option(result).flatMap(r => Option(r.getOpenAPI)).getOrElse {
      // Don't echo the full raw content — it may contain URLs with tokens or large YAML.
      // The parse messages (logged above) are the useful diagnostic.
      System.err.println(
        s"Baklava: unable to parse openapi-info; falling back to empty OpenAPI. Raw content length: ${raw.length}, prefix: '${raw
            .take(120)}${if (raw.length > 120) "..." else ""}'"
      )
      new OpenAPI()
    }
  }
}
