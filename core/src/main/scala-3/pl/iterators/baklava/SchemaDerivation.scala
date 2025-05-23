package pl.iterators.baklava

import magnolia1.*

trait SchemaDerivation extends AutoDerivation[Schema] {
  def join[T](caseClass: CaseClass[Schema, T]): Schema[T] = new Schema[T] {
    val className: String      = caseClass.typeInfo.short
    val `type`: SchemaType     = SchemaType.ObjectType
    val format: Option[String] = None
    val properties: Map[String, Schema[?]] = caseClass.parameters.map { p =>
      p.default match {
        case Some(default) => p.label -> p.typeclass.withDefault(default) // certainly not perfect as it depends on JSON serialization
        case None          => p.label -> p.typeclass
      }
    }.toMap
    val items: Option[Schema[?]]      = None
    val `enum`: Option[Set[String]]   = None
    val required: Boolean             = true
    val additionalProperties: Boolean = false
    val default: Option[T]            = None
    val description: Option[String]   = None
  }

  def split[T](sealedTrait: SealedTrait[Schema, T]): Schema[T] = new Schema[T] {
    val className: String                     = sealedTrait.typeInfo.short
    val `type`: SchemaType                    = SchemaType.StringType
    val format: Option[String]                = None
    val properties: Map[String, Typeclass[?]] = Map.empty
    val items: Option[Typeclass[?]]           = None
    val `enum`: Option[Set[String]]           = Some(sealedTrait.subtypes.map(_.typeInfo.short).toSet)
    val required: Boolean                     = true
    val additionalProperties: Boolean         = false
    val default: Option[T]                    = None
    val description: Option[String]           = None
  }
}
