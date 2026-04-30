package pl.iterators.baklava.sttpclient

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

/** Emits a Scala source tree under `target/baklava/sttpclient/` that users drop into their own project. Output structure mirrors the
  * tsfetch generator: one sub-package per operation tag with its own `{Tag}Endpoints.scala` (endpoint object) and `dtos.scala` (case
  * classes), plus a `common` sub-package for DTOs shared between two or more tags. Endpoints with named case-class bodies return typed
  * `sttp.client4.Request[Either[sttp.client4.ResponseException[String], T]]` values using `sttp.client4.circe._`; non-JSON and unnamed
  * bodies fall back to the raw `Request[Either[String, String]]` shape. Callers pick their own backend.
  */
class BaklavaDslFormatterSttpClient extends BaklavaDslFormatter {

  private val dirName = "target/baklava/sttpclient"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    val basePackage = config.getOrElse("sttp-client-package", "baklavaclient")
    val basePath    = s"$dirName/src/main/scala/${basePackage.replace('.', '/')}"
    new File(basePath).mkdirs()

    val gen = new BaklavaSttpClientGenerator(basePackage, calls)

    // Common types (optional).
    gen.renderSharedTypes.foreach { content =>
      writeFile(s"$basePath/common/dtos.scala", content)
    }
    // Per-tag subfolders.
    gen.renderTagFiles.foreach { case (relPath, content) =>
      writeFile(s"$basePath/$relPath", content)
    }
    writeFile(s"$dirName/README.md", gen.renderReadme)
  }

  private def writeFile(path: String, content: String): Unit = {
    new File(path).getParentFile.mkdirs()
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
  }
}
