package pl.iterators.baklava.tscommon

import pl.iterators.baklava.*

/** Named schema hoisting shared by the zod-emitting formatters: every object schema with a usable captured case-class name is hoisted into
  * a schemas file under a derived name (`AuctionDto` → `auctionDtoSchema`) and paired with its inferred type, so contracts read as wiring
  * and the schemas files as the domain vocabulary. The same derived name with a different structure gets a deterministic hash suffix;
  * anonymous shapes (params/query groups, `Map`s) stay inline.
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

  /** `rendered` is every schema the formatter will emit (bodies, responses, error data — nested occurrences included). `definitionOf`
    * renders a schema standalone and is used only for deterministic collision ordering and hash suffixes.
    */
  def buildRefs(
      rendered: Seq[BaklavaSchemaSerializable],
      definitionOf: BaklavaSchemaSerializable => String
  ): Map[BaklavaSchemaSerializable, String] = {
    val hoistable = rendered.flatMap(collectObjectNodes).distinct
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

  /** name → hoisted names its definition references directly. `definitionWithRefs` must render the schema while reporting each refs-map
    * hit to the callback (the formatters' recording renderer does exactly this).
    */
  def definitionUses(
      refs: Map[BaklavaSchemaSerializable, String],
      definitionWithRefs: (BaklavaSchemaSerializable, String => Unit) => String
  ): Map[String, Set[String]] =
    refs.map { case (schema, name) =>
      val used = scala.collection.mutable.Set.empty[String]
      val _    = definitionWithRefs(schema, used += _)
      name -> (used.toSet - name)
    }

  /** Which modules (transitively) use each hoisted name: a module using A also uses everything A's definition references. A name used by
    * exactly one module can live in that module's local schema file; anything else belongs in the shared one.
    */
  def moduleAssignment(
      directUsage: Seq[(String, Set[String])],
      defUses: Map[String, Set[String]]
  ): Map[String, Set[String]] = {
    val perModule = directUsage.map { case (moduleId, direct) =>
      val acc      = scala.collection.mutable.Set.from(direct)
      var frontier = direct
      while (frontier.nonEmpty) {
        val next = frontier.flatMap(defUses.getOrElse(_, Set.empty)).diff(acc)
        acc ++= next
        frontier = next
      }
      moduleId -> acc.toSet
    }
    perModule
      .flatMap { case (moduleId, names) => names.map(_ -> moduleId) }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSet)
      .toMap
  }

  /** A schema file's body: definitions ordered dependencies-first (a hoisted schema may reference another). `definitionWithRefs` must
    * render the schema itself inline while resolving nested hoisted schemas through the refs map. `importLines` lets a module-local file
    * import the shared names its definitions reference.
    */
  def schemasFileContent(
      refs: Map[BaklavaSchemaSerializable, String],
      definitionWithRefs: BaklavaSchemaSerializable => String,
      importLines: Seq[String] = Seq.empty
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
      .map { case (schema, name) =>
        s"export const $name = ${definitionWithRefs(schema)};\nexport type ${typeNameOf(name)} = z.infer<typeof $name>;"
      }
      .mkString("\n\n")
    val imports = ("import { z } from \"zod\";" +: importLines).mkString("\n")
    imports + "\n\n" + defs + "\n"
  }

  // Importing a type named like a TS/DOM global (`Error`, `Date`, …) shadows it in the consuming
  // module — a silent behavior change, not an error — so those get an explicit suffix.
  private val shadowableGlobals = Set(
    "Error",
    "Date",
    "File",
    "Blob",
    "Object",
    "Array",
    "Map",
    "Set",
    "Promise",
    "Function",
    "Symbol",
    "Request",
    "Response",
    "Event",
    "Node",
    "Text",
    "Comment",
    "Record",
    "Partial",
    "Readonly",
    "Pick",
    "Omit"
  )

  /** `auctionDtoSchema` → `AuctionDto`, `errorSchema` → `ErrorType` (shadowable global), collision hashes carry over
    * (`auctionDtoSchema4496` → `AuctionDto4496`).
    */
  private[tscommon] def typeNameOf(constName: String): String = {
    val idx      = constName.lastIndexOf("Schema")
    val stripped = if (idx >= 0) constName.substring(0, idx) + constName.substring(idx + "Schema".length) else constName
    val base     = TsNaming.capitalize(stripped)
    if (shadowableGlobals.contains(base)) base + "Type" else base
  }
}
