package pl.iterators.baklava

import org.reflections.Reflections

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait BaklavaDslFormatter {
  def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit
}

object BaklavaDslFormatter {
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
