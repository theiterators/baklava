package pl.iterators.baklava

trait SchemaDerivation extends SchemaDerivationCompat {
  // override to match the codec's wire naming (e.g. SchemaNameTransform.snakeCase)
  def transformMemberName(name: String): String = name

  // called by the derivation macro
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
}

object SchemaDerivation {
  final class Field(val label: String, val schema: Schema[?], val default: Option[Any])
}
