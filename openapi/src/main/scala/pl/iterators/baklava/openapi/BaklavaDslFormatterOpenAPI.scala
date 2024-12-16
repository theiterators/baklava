package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Json
import io.swagger.v3.parser.OpenAPIV3Parser
import pl.iterators.baklava.{BaklavaDslFormatter, BaklavaRequestContext, BaklavaResponseContext}

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URLEncoder
import scala.util.Using

//todo
class BaklavaDslFormatterOpenAPI extends BaklavaDslFormatter {
//todo names - check if its possible to read the target name from context or something like thath
  private val dirName        = "target/baklava"
  private val dirFile        = new File(dirName)
  private val chunkExtension = "openapi.chunk"

  override def createChunk(
      name: String,
      pathRepresentation: List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]
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

  override def mergeChunks(): Unit = {
    println("running merge chunks")
    val chunks = Option(dirFile.listFiles())
      .getOrElse(Array.empty[File])
      .filter(_.getName.endsWith(chunkExtension))
      .flatMap { file =>
        Using(new FileInputStream(file)) { chunkInputStream =>
          // todo
          val byteArray = new Array[Byte](file.length().toInt)
          chunkInputStream.read(byteArray)

          val jsonString = new String(byteArray)

          val parser = new OpenAPIV3Parser
          parser.readContents(jsonString, null, null).getOpenAPI
        }.toOption
      }
      .toList

    val finalFile = new File(s"$dirName/baklava.openapi")

    Using(new FileOutputStream(finalFile)) { outputStream =>

      val jsonString = Json.pretty(OpenAPIGenerator.merge(chunks))

      outputStream.write(jsonString.getBytes)
    }.recover { case e: Exception =>
      // todo
      println(s"Failed to write to file: $e")
    }
    ()
  }
}
