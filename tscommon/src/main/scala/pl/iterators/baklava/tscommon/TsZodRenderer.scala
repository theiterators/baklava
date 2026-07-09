package pl.iterators.baklava.tscommon

import pl.iterators.baklava.*

/** The per-format zod vocabulary. The TypeScript-emitting formatters share one renderer; the few places where their output legitimately
  * differs are captured here instead of forked code.
  */
final case class TsZodDialect(
    dateTime: String,
    email: String,
    uuid: String,
    filePart: String,
    emptyUnion: String
)

object TsZodDialect {
  val tsRest: TsZodDialect =
    TsZodDialect(
      dateTime = "z.coerce.date()",
      email = "z.string().email()",
      uuid = "z.string().uuid()",
      filePart = "z.instanceof(File)",
      emptyUnion = "z.undefined()"
    )

  // zod 4 vocabulary: top-level string formats and the dedicated file schema.
  val orpc: TsZodDialect =
    TsZodDialect(
      dateTime = "z.iso.datetime({ offset: true })",
      email = "z.email()",
      uuid = "z.uuid()",
      filePart = "z.file()",
      emptyUnion = "z.void()"
    )
}

object TsNaming {

  def capitalize(s: String): String = if (s.isEmpty) s else s"${s.head.toUpper}${s.tail}"

  /** Camelize one raw path segment into a router-object key: runs of non-alphanumerics are word boundaries (`feature-flags` ->
    * `featureFlags`, `file.txt` -> `fileTxt`); an already-camelCase segment passes through.
    */
  def segmentCamel(seg: String): String = {
    val parts = seg.split("[^A-Za-z0-9]+").filter(_.nonEmpty).toList
    parts match {
      case Nil          => "root"
      case head :: tail => (decapitalize(head) :: tail.map(capitalize)).mkString
    }
  }

  private def decapitalize(s: String): String = if (s.isEmpty) s else s"${s.head.toLower}${s.tail}"

  def isPathParamSegment(seg: String): Boolean =
    (seg.startsWith("{") && seg.endsWith("}")) || seg.startsWith(":")

  /** Router-object key for a path segment. A path parameter reads as `by<Param>` (`{auctionId}` -> `byAuctionId`) — the same convention
    * tsfetch uses in function names (`getUsersByUserId`); static segments are camelized.
    */
  def segmentKey(seg: String): String =
    if (isPathParamSegment(seg)) {
      val raw = if (seg.startsWith("{")) seg.substring(1, seg.length - 1) else seg.stripPrefix(":")
      "by" + capitalize(segmentCamel(raw))
    } else segmentCamel(seg)
}

class TsZodRenderer(dialect: TsZodDialect, refs: BaklavaSchemaSerializable => Option[String] = _ => None) {

  /** Render a hoisted schema's defining expression: the node itself inlines, nested schemas may still resolve through `refs`.
    */
  def zodDefinition(schema: BaklavaSchemaSerializable): String = zodInline(schema)

  private val jsIdentifier = "[A-Za-z_$][A-Za-z0-9_$]*".r

  // An object key may be written bare only if it's a valid JS identifier; query/header/path-param
  // names can be kebab-case (`seller-id`, `X-Forwarded-For`) or start with a digit, which would
  // otherwise produce uncompilable TypeScript — so quote anything that isn't identifier-shaped.
  def tsObjectKey(name: String): String =
    if (jsIdentifier.matches(name)) name
    else s""""${escapeTsDoubleQuoted(name)}""""

  def escapeTsSingleQuoted(s: String): String =
    s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")

  def escapeTsDoubleQuoted(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  def isEmptyBodyInstance(schema: BaklavaSchemaSerializable): Boolean =
    schema.`type` == SchemaType.StringType &&
      schema.`enum`.exists(enums => enums.contains("EmptyBodyInstance") && enums.size == 1)

  def buildParamsZod[P](
      paramsPerCall: Seq[Seq[P]],
      nameOf: P => String,
      schemaOf: P => BaklavaSchemaSerializable
  ): Option[String] = {
    val distinctSets = paramsPerCall.distinct
    if (!distinctSets.exists(_.nonEmpty)) None
    else {
      val zds = distinctSets.map { params =>
        val fields = params.map { p =>
          val nullishMaybe = if (!schemaOf(p).required) ".nullish()" else ""
          s"${tsObjectKey(nameOf(p))}: ${zod(schemaOf(p))}$nullishMaybe"
        }
        "z.object({" + fields.mkString(", ") + "})"
      }
      Some(collapseZodUnion(zds))
    }
  }

  // Render one captured `Multipart` value as a body schema: a `z.object` keyed by part name,
  // `FilePart` -> the dialect's file schema, `TextPart` -> `z.string()`. A repeated part name
  // (a multi-value form field) becomes a `z.array(...)`; a name that mixes file and text parts
  // unions the element schemas. Names and element schemas are sorted so output is deterministic.
  def renderMultipartBody(parts: Seq[BaklavaMultipartPartSerializable]): String = {
    val fields = parts
      .groupBy(_.name)
      .toSeq
      .sortBy(_._1)
      .map { case (name, ps) =>
        val element = collapseZodUnion(ps.map(p => if (p.isFile) dialect.filePart else "z.string()").sorted)
        val schema  = if (ps.size > 1) s"z.array($element)" else element
        s"${tsObjectKey(name)}: $schema"
      }
    s"z.object({${fields.mkString(", ")}})"
  }

  def zod(schema: BaklavaSchemaSerializable): String =
    refs(schema).getOrElse(zodInline(schema))

  private def zodInline(schema: BaklavaSchemaSerializable): String = {
    val desc = schema.description.map(d => s""".describe("${escapeTsDoubleQuoted(d)}")""").getOrElse("")
    schema.`type` match {
      case SchemaType.StringType =>
        if (schema.`enum`.exists(_.nonEmpty)) {
          // Sort for deterministic output; escape for double-quoted TS string context.
          val e = schema.`enum`.get.toList.sorted.map(s => "\"" + escapeTsDoubleQuoted(s) + "\"").mkString(",")
          s"z.enum([$e])$desc"
        } else if (schema.format.contains("email")) s"${dialect.email}$desc"
        else if (schema.format.contains("uuid")) s"${dialect.uuid}$desc"
        else if (schema.format.contains("date-time")) s"${dialect.dateTime}$desc"
        else s"z.string()$desc"
      case SchemaType.BooleanType => s"z.boolean()$desc"
      case SchemaType.IntegerType => s"z.number().int()$desc"
      case SchemaType.NumberType  => s"z.number()$desc"
      case SchemaType.ArrayType   =>
        val item = schema.items.map(zod).getOrElse("z.any()")
        s"z.array($item)$desc"
      case SchemaType.ObjectType =>
        val objectBody =
          if (schema.properties.isEmpty) "z.object({})"
          else {
            val props = schema.properties.toSeq
              .sortBy(_._1)
              .map { case (k, v) =>
                s""""${escapeTsDoubleQuoted(k)}": ${zod(v)}${if (!v.required) ".nullish()" else ""}"""
              }
              .mkString("\n        ", ",\n        ", "")
            s"z.object({$props})"
          }
        schema.additionalPropertiesSchema match {
          // A map-like object: all values conform to one schema -> z.record (keys are strings in JSON).
          case Some(v) if schema.properties.isEmpty => s"z.record(z.string(), ${zod(v)})$desc"
          case Some(v)                              => s"$objectBody.catchall(${zod(v)})$desc"
          case None                                 => s"$objectBody$desc"
        }
      case SchemaType.NullType => s"z.null()$desc"
    }
  }

  def collapseZodUnion(zods: Seq[String]): String = {
    val distinct = zods.distinct
    if (distinct.isEmpty) dialect.emptyUnion
    else if (distinct.size == 1) distinct.head
    else s"z.union([${distinct.mkString(", ")}])"
  }

}
