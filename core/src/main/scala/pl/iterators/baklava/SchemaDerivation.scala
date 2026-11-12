package pl.iterators.baklava

/** Derives [[Schema]] instances for case classes (object schemas) and sealed traits / enums (string enum schemas).
  *
  *   - `derived[T]` derives explicitly: `implicit val dtoSchema: Schema[Dto] = Schema.derived[Dto]`, or `derives Schema` on Scala 3.
  *   - the automatic instance (`autoDerived` on Scala 3, `gen` on Scala 2) kicks in wherever a `Schema[T]` of a case class or sealed trait
  *     is needed and none is in scope. Disable it with `-Xmacro-settings:baklava.schema.autoDerivation=false` to make a missing instance a
  *     compile error.
  *
  * Either way, nested types are derived once per derivation and any instance already in scope for a nested type is used as is.
  */
trait SchemaDerivation extends SchemaDerivationCompat {
  // override to match the codec's wire naming (e.g. SchemaNameTransform.snakeCase)
  def transformMemberName(name: String): String      = name
  def transformConstructorName(name: String): String = name

  // Called by the derivation macro; not meant to be called directly.
  def deriveProduct[T](className: String, fields: List[SchemaDerivation.Field]): Schema[T] = {
    val _className = className
    new Schema[T] {
      val className: String                  = _className
      val `type`: SchemaType                 = SchemaType.ObjectType
      val format: Option[String]             = None
      val properties: Map[String, Schema[?]] = fields.map { field =>
        field.default match {
          // certainly not perfect as it depends on JSON serialization
          case Some(default) => transformMemberName(field.label) -> field.schema.asInstanceOf[Schema[Any]].withDefault(default)
          case None          => transformMemberName(field.label) -> field.schema
        }
      }.toMap
      val items: Option[Schema[?]]      = None
      val `enum`: Option[Set[String]]   = None
      val required: Boolean             = true
      val additionalProperties: Boolean = false
      val default: Option[T]            = None
      val description: Option[String]   = None
    }
  }

  // Called by the derivation macro; not meant to be called directly.
  def deriveEnum[T](className: String, constructors: List[String]): Schema[T] = {
    val _className = className
    new Schema[T] {
      val className: String                  = _className
      val `type`: SchemaType                 = SchemaType.StringType
      val format: Option[String]             = None
      val properties: Map[String, Schema[?]] = Map.empty
      val items: Option[Schema[?]]           = None
      val `enum`: Option[Set[String]]        = Some(constructors.map(transformConstructorName).toSet)
      val required: Boolean                  = true
      val additionalProperties: Boolean      = false
      val default: Option[T]                 = None
      val description: Option[String]        = None
    }
  }
}

object SchemaDerivation {
  final class Field(val label: String, val schema: Schema[?], val default: Option[Any])
}
