package pl.iterators.baklava.openapi

import pl.iterators.baklava.{BaklavaDslFormatter, BaklavaRequestContext, BaklavaResponseContext}

import java.io.{File, FileInputStream, FileOutputStream}
import java.net.URLEncoder
import scala.util.Using

//todo
class BaklavaDslFormatterOpenAPI extends BaklavaDslFormatter {
//todo names - chatck if its possible to read the target name from context or something like thath
  private val dirName        = "target/baklava"
  private val dirFile        = new File(dirName)
  private val chunkExtension = "openapichunk"

  override def createChunk(pathRepresentation: List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]): Unit = {
    dirFile.mkdirs()

    val symbolicPath = pathRepresentation.head._1.symbolicPath
    val chunkFile    = new File(s"$dirName/${URLEncoder.encode(symbolicPath, "UTF-8")}.$chunkExtension")

    Using(new FileOutputStream(chunkFile)) { outputStream =>
      outputStream.write(symbolicPath.getBytes)
    }.recover { case e: Exception =>
      // todo
      println(s"Failed to write to file: $e")
    }
  }

  override def mergeChunks(): Unit = {
    println("running merge chunks")
    val finalFile = new File(s"$dirName/finalfile.openapi")
    Using(new FileOutputStream(finalFile)) { outputStream =>
      Option(dirFile.listFiles()).getOrElse(Array.empty).filter(_.getName.endsWith(chunkExtension)).map { file =>
        println(file.getName)
        Using(new FileInputStream(file)) { chunkInputStream =>
          // todo
          val byteArray = new Array[Byte](file.length().toInt)
          chunkInputStream.read(byteArray)
          outputStream.write(byteArray)
        }
      }
    }
  }
}
