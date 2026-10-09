package pl.iterators.baklava

import org.reflections.Reflections

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait BaklavaDslFormatter {

  /** Writes the full output under the default root, `target/baklava/<formatter>`. */
  def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit =
    create(config, calls, BaklavaDslFormatter.DefaultOutputRoot)

  /** Writes the output under `<outputRoot>/<formatter>`. Views (see [[BaklavaView]]) run every formatter again with a filtered set of
    * calls and a root of their own, so a formatter must derive every path it writes from `outputRoot`.
    */
  def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall], outputRoot: String): Unit
}

object BaklavaDslFormatter {
  val DefaultOutputRoot: String = "target/baklava"

  lazy val formatters: Seq[BaklavaDslFormatter] = {
    lazy val inner = new Reflections("pl.iterators.baklava")
      .getSubTypesOf(classOf[BaklavaDslFormatter])
      .asScala
      .map { specClazz =>
        specClazz.getConstructor().newInstance()
      }
      .toSeq

    if (inner.isEmpty) {
      sys.error(
        "No BaklavaDslFormatter implementations were found on the classpath. " +
          "Add one of: `baklava-openapi`, `baklava-simple`, `baklava-tsrest` to your project's dependencies, " +
          "or remove the call to BaklavaDslFormatter.formatters if no documentation output is intended."
      )
    }
    inner
  }

}
