package pl.iterators.baklava.sttpclient

import pl.iterators.baklava.*

import java.io.{File, FileWriter, PrintWriter}
import scala.util.Using

/** Emits a Scala source tree under `target/baklava/sttpclient/` that users drop into their own project. Output is intentionally minimal: a
  * single `Types.scala` with case classes derived from named object schemas, plus one file per operation tag with endpoint-builder methods
  * that return `sttp.client4.Request[Either[String, String], Any]`. Users pick their own sttp backend and JSON codec library and call
  * `.send(backend)` themselves — this generator has no opinion on effect type or serialization format.
  */
class BaklavaDslFormatterSttpClient extends BaklavaDslFormatter {

  private val dirName        = "target/baklava/sttpclient"
  private val sourcesDirName = s"$dirName/src/main/scala/baklavaclient"

  override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit = {
    new File(sourcesDirName).mkdirs()

    val packageName = config.getOrElse("sttp-client-package", "baklavaclient")
    val gen         = new BaklavaSttpClientGenerator(packageName, calls)

    writeFile(s"$sourcesDirName/Types.scala", gen.renderTypes)
    gen.renderTagFiles.foreach { case (fileName, content) =>
      writeFile(s"$sourcesDirName/$fileName", content)
    }
    writeFile(s"$dirName/README.md", gen.renderReadme)
  }

  private def writeFile(path: String, content: String): Unit =
    Using.resource(new PrintWriter(new FileWriter(path)))(_.write(content))
}
