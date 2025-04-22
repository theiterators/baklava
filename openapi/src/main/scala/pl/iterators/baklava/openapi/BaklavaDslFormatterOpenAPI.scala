package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import pl.iterators.baklava.*

import java.io.{File, FileOutputStream}
import scala.util.Using

class BaklavaDslFormatterOpenAPI extends BaklavaDslFormatter {

  private val dirName = "target/baklava/openapi"
  private val dirFile = new File(dirName)

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    dirFile.mkdirs()
    val openapiFile = new File(s"$dirName/openapi.yml")

    Using(new FileOutputStream(openapiFile)) { outputStream =>
      val parser = new OpenAPIV3Parser
      val openAPI = config
        .get("openapi-info")
        .flatMap { openApiHeader =>
          Option {
            parser.readContents(openApiHeader, null, null).getOpenAPI
          }.orElse {
            println(s"Unable to parse your openapi-info -> '$openApiHeader''")
            None
          }
        }
        .getOrElse(new OpenAPI())

      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, calls)

      BaklavaOpenApiPostProcessor.postProcessors.foreach(_.process(openAPI))

      val ymlString = Yaml.pretty(openAPI)

      outputStream.write(ymlString.getBytes)

    }.recover { case e: Exception =>
      println(s"Failed to write to file: $e")
    }
    ()
  }
}
