package pl.iterators.baklava.tscommon

import pl.iterators.baklava.*

/** Named, deduplicated schema hoisting shared by the zod-emitting formatters: object schemas occurring more than once anywhere in the
  * rendered output are hoisted into `schemas.ts` under a name derived from the captured case-class name (`AuctionDto` →
  * `auctionDtoSchema`), giving consumers `z.infer`-able named types. The same derived name with a different structure gets a deterministic
  * hash suffix.
  */
object TsSchemaRefs {

  def collectObjectNodes(schema: BaklavaSchemaSerializable): Seq[BaklavaSchemaSerializable] = {
    val children =
      schema.properties.values.toSeq ++ schema.items.toSeq ++ schema.additionalPropertiesSchema.toSeq
    val self =
      if (schema.`type` == SchemaType.ObjectType && schema.properties.nonEmpty) Seq(schema) else Seq.empty
    self ++ children.flatMap(collectObjectNodes)
  }

  private val genericClassNames = Set("Object", "Map", "Option", "Some", "None", "List", "Seq", "Vector", "Set")

  private def hoistableName(schema: BaklavaSchemaSerializable): Option[String] =
    Option(schema.className)
      .filter(_.matches("[A-Za-z][A-Za-z0-9]*"))
      .filterNot(genericClassNames.contains)
      .map(n => n.head.toLower.toString + n.tail + "Schema")

  /** `rendered` is every schema the formatter will emit (bodies, responses, error data — nested occurrences count too). `definitionOf`
    * renders a schema standalone and is used only for deterministic collision ordering and hash suffixes.
    */
  def buildRefs(
      rendered: Seq[BaklavaSchemaSerializable],
      definitionOf: BaklavaSchemaSerializable => String
  ): Map[BaklavaSchemaSerializable, String] = {
    val counts    = rendered.flatMap(collectObjectNodes).groupBy(identity).view.mapValues(_.size).toMap
    val hoistable = counts.collect { case (schema, n) if n >= 2 => schema }.toSeq
    val named     = hoistable.flatMap(s => hoistableName(s).map(_ -> s))
    named
      .groupBy(_._1)
      .toSeq
      .flatMap { case (name, entries) =>
        val schemas = entries.map(_._2).sortBy(definitionOf)
        schemas.zipWithIndex.map { case (schema, i) =>
          val finalName = if (i == 0) name else s"$name${f"${definitionOf(schema).hashCode.abs}%x".take(4)}"
          schema -> finalName
        }
      }
      .toMap
  }

  /** The `schemas.ts` body: definitions ordered dependencies-first (a hoisted schema may reference another). `definitionWithRefs` must
    * render the schema itself inline while resolving nested hoisted schemas through the refs map.
    */
  def schemasFileContent(
      refs: Map[BaklavaSchemaSerializable, String],
      definitionWithRefs: BaklavaSchemaSerializable => String
  ): String = {
    val remaining = scala.collection.mutable.LinkedHashMap.from(refs.toSeq.sortBy(_._2))
    val ordered   = scala.collection.mutable.ListBuffer.empty[(BaklavaSchemaSerializable, String)]
    while (remaining.nonEmpty) {
      val ready = remaining.filter { case (schema, _) =>
        collectObjectNodes(schema).filterNot(_ == schema).forall(n => !remaining.contains(n))
      }
      ready.foreach { entry =>
        ordered += entry
        remaining -= entry._1
      }
    }
    val defs = ordered
      .map { case (schema, name) => s"export const $name = ${definitionWithRefs(schema)};" }
      .mkString("\n\n")
    "import { z } from \"zod\";\n\n" + defs + "\n"
  }
}
