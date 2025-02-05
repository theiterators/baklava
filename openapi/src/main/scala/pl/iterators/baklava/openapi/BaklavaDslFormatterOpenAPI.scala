package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.{Json, Yaml}
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import pl.iterators.baklava.{BaklavaDslFormatter, BaklavaRequestContext, BaklavaResponseContext}

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URLEncoder
import scala.util.Using

class BaklavaDslFormatterOpenAPI extends BaklavaDslFormatter {
//todo names - check if its possible to read the target name from context or something like that
  private val dirName        = "target/baklava/openapi"
  private val dirFile        = new File(dirName)
  private val chunkExtension = "openapi.chunk"

  override def createChunk(
      name: String,
      pathRepresentation: List[(BaklavaRequestContext[?, ?, ?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]
  ): Unit = {
    dirFile.mkdirs()

    val chunkFile = new File(s"$dirName/${URLEncoder.encode(name, "UTF-8")}.$chunkExtension")

    val jsonString = Json.pretty(OpenAPIGenerator.chunk(pathRepresentation))

    Using(new FileOutputStream(chunkFile)) { outputStream =>
      outputStream.write(jsonString.getBytes)
    }.recover { case e: Exception =>
      // todo
      println(s"Failed to write to file: $e")
    }
    ()
  }

  override def mergeChunks(config: Map[String, String]): Unit = {
    dirFile.mkdirs()

    val chunks = Option(dirFile.listFiles())
      .getOrElse(Array.empty[File])
      .filter(_.getName.endsWith(chunkExtension))
      .flatMap { file =>
        Using(new FileInputStream(file)) { chunkInputStream =>
          val byteArray = new Array[Byte](file.length().toInt)
          chunkInputStream.read(byteArray)

          val jsonString = new String(byteArray)

          val parser = new OpenAPIV3Parser
          parser.readContents(jsonString, null, null).getOpenAPI
        }.toOption
      }
      .toList

    val finalFile = new File(s"$dirName/openapi.yml")

    Using(new FileOutputStream(finalFile)) { outputStream =>
      val parser = new OpenAPIV3Parser
      val openApiHeaderChunk = config
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

      val ymlString = Yaml.pretty(OpenAPIGenerator.merge(openApiHeaderChunk, chunks))

      outputStream.write(ymlString.getBytes)
    }.recover { case e: Exception =>
      // todo
      println(s"Failed to write to file: $e")
    }
    ()
  }
}
