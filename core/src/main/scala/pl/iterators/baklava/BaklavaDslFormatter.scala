package pl.iterators.baklava

import org.reflections.Reflections

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait BaklavaDslFormatter {
  def createChunk(name: String, pathRepresentation: List[(BaklavaRequestContext[?, ?, ?, ?, ?], BaklavaResponseContext[?, ?, ?])]): Unit

  def mergeChunks(config: Map[String, String]): Unit
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
      // todo better message and probably only warn or info or debug or delete it at all
      sys.error(s"Unable to find class formatters")
    }
    inner
  }

}
