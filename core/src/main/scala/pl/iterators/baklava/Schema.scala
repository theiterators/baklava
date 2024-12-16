package pl.iterators.baklava

sealed trait SchemaType
object SchemaType {
  case object NullType extends SchemaType

  case object StringType extends SchemaType

  case object BooleanType extends SchemaType

  case object IntegerType extends SchemaType

  case object NumberType extends SchemaType

  case object ArrayType extends SchemaType

  case object ObjectType extends SchemaType
}

trait Schema[T] {
  val className: String
  val `type`: SchemaType
  val format: Option[String]
  val properties: Map[String, Schema[?]]
  val items: Option[Schema[?]]
  val `enum`: Option[Set[String]]
  val required: Boolean
  val additionalProperties: Boolean
  val default: Option[T]
  val description: Option[String]

  def withDescription(_description: String): Schema[T] = new Schema[T] {
    val className: String                  = Schema.this.className
    val `type`: SchemaType                 = Schema.this.`type`
    val format: Option[String]             = Schema.this.format
    val properties: Map[String, Schema[?]] = Schema.this.properties
    val items: Option[Schema[?]]           = Schema.this.items
    val `enum`: Option[Set[String]]        = Schema.this.`enum`
    val required: Boolean                  = Schema.this.required
    val additionalProperties: Boolean      = Schema.this.additionalProperties
    val default: Option[T]                 = Schema.this.default
    val description: Option[String]        = Some(_description)
  }

  def withDefault(_default: T): Schema[T] = new Schema[T] {
    val className: String                  = Schema.this.className
    val `type`: SchemaType                 = Schema.this.`type`
    val format: Option[String]             = Schema.this.format
    val properties: Map[String, Schema[?]] = Schema.this.properties
    val items: Option[Schema[?]]           = Schema.this.items
    val `enum`: Option[Set[String]]        = Schema.this.`enum`
    val required: Boolean                  = Schema.this.required
    val additionalProperties: Boolean      = Schema.this.additionalProperties
    val default: Option[T]                 = Some(_default)
    val description: Option[String]        = Schema.this.description
  }

  override def toString: String = {
    `type` match {
      case _ if `enum`.isDefined  => s"enum(${`enum`.get.mkString(", ")})"
      case SchemaType.NullType    => "null"
      case SchemaType.StringType  => "string"
      case SchemaType.BooleanType => "boolean"
      case SchemaType.IntegerType => "integer"
      case SchemaType.NumberType  => "number"
      case SchemaType.ArrayType   => s"array(${items.getOrElse("unknown")})"
      case SchemaType.ObjectType =>
        s"object(${properties.map { case (k, v) => k + (if (v.required) "*" else "") + ": " + v.toString }.mkString(", ")})"
    }
  }
}

object Schema extends SchemaDerivation {
  implicit val emptyBodySchema: Schema[EmptyBody] = new Schema[EmptyBody] {
    val className: String                  = "EmptyBody"
    val `type`: SchemaType                 = SchemaType.NullType
    val format: Option[String]             = None
    val properties: Map[String, Schema[?]] = Map.empty
    val items: Option[Schema[?]]           = None
    val `enum`: Option[Set[String]]        = None
    val required: Boolean                  = false
    val additionalProperties: Boolean      = false
    val default: Option[EmptyBody]         = None
    val description: Option[String]        = None
  }
  implicit val intSchema: PrimitiveSchema[Int]               = PrimitiveSchema[Int]("Int", SchemaType.IntegerType, Some("int32"))
  implicit val longSchema: PrimitiveSchema[Long]             = PrimitiveSchema[Long]("Long", SchemaType.IntegerType, Some("int64"))
  implicit val floatSchema: PrimitiveSchema[Float]           = PrimitiveSchema[Float]("Float", SchemaType.NumberType, Some("float"))
  implicit val doubleSchema: PrimitiveSchema[Double]         = PrimitiveSchema[Double]("Double", SchemaType.NumberType, Some("double"))
  implicit val stringSchema: PrimitiveSchema[String]         = PrimitiveSchema[String]("String", SchemaType.StringType, None)
  implicit val byteSchema: PrimitiveSchema[Byte]             = PrimitiveSchema[Byte]("Byte", SchemaType.IntegerType, None)
  implicit val shortSchema: PrimitiveSchema[Short]           = PrimitiveSchema[Short]("Short", SchemaType.IntegerType, None)
  implicit val booleanSchema: PrimitiveSchema[Boolean]       = PrimitiveSchema[Boolean]("Boolean", SchemaType.BooleanType, None)
  implicit val nullSchema: PrimitiveSchema[Null]             = PrimitiveSchema[Null]("Null", SchemaType.NullType, None)
  implicit val unitSchema: PrimitiveSchema[Unit]             = PrimitiveSchema[Unit]("Unit", SchemaType.NullType, None)
  implicit val bigDecimalSchema: PrimitiveSchema[BigDecimal] = PrimitiveSchema[BigDecimal]("BigDecimal", SchemaType.NumberType, None)
  implicit def optionSchema[T](implicit schema: Schema[T]): Schema[Option[T]] = new Schema[Option[T]] {
    val className: String                  = schema.className
    val `type`: SchemaType                 = schema.`type`
    val format: Option[String]             = schema.format
    val properties: Map[String, Schema[?]] = schema.properties
    val items: Option[Schema[?]]           = schema.items
    val `enum`: Option[Set[String]]        = schema.`enum`
    val required: Boolean                  = false
    val additionalProperties: Boolean      = schema.additionalProperties
    val default: Option[Option[T]]         = Some(None)
    val description: Option[String]        = schema.description
  }
  implicit def seqSchema[T](implicit schema: Schema[T]): Schema[Seq[T]] = new Schema[Seq[T]] {
    val className: String                  = schema.className
    val `type`: SchemaType                 = SchemaType.ArrayType
    val format: Option[String]             = None
    val properties: Map[String, Schema[?]] = Map.empty
    val items: Option[Schema[?]]           = Some(schema)
    val `enum`: Option[Set[String]]        = None
    val required: Boolean                  = true
    val additionalProperties: Boolean      = false
    val default: Option[Seq[T]]            = None
    val description: Option[String]        = None
  }
  implicit def listSchema[T](implicit schema: Schema[T]): Schema[List[T]] = new Schema[List[T]] {
    val className: String                  = schema.className
    val `type`: SchemaType                 = SchemaType.ArrayType
    val format: Option[String]             = None
    val properties: Map[String, Schema[?]] = Map.empty
    val items: Option[Schema[?]]           = Some(schema)
    val `enum`: Option[Set[String]]        = None
    val required: Boolean                  = true
    val additionalProperties: Boolean      = false
    val default: Option[List[T]]           = None
    val description: Option[String]        = None
  }
  implicit def stringMapSchema[T](implicit schema: Schema[T]): Schema[Map[String, T]] = new Schema[Map[String, T]] {
    val className: String                  = "Map[String, " + schema.className + "]"
    val `type`: SchemaType                 = SchemaType.ObjectType
    val format: Option[String]             = None
    val properties: Map[String, Schema[?]] = Map.empty
    val items: Option[Schema[?]]           = None
    val `enum`: Option[Set[String]]        = None
    val required: Boolean                  = true
    val additionalProperties: Boolean      = true
    val default: Option[Map[String, T]]    = None
    val description: Option[String]        = None
  }
  implicit def uuidSchema: PrimitiveSchema[java.util.UUID] = PrimitiveSchema[java.util.UUID]("UUID", SchemaType.StringType, Some("uuid"))
  // TODO: java.time.*
  // TODO: java.util.*
  // TODO: arrays, collections
}

case class PrimitiveSchema[T](
    className: String,
    `type`: SchemaType,
    format: Option[String]
) extends Schema[T] {
  val properties: Map[String, Schema[?]] = Map.empty
  val items: Option[Schema[?]]           = None
  val `enum`: Option[Set[String]]        = None
  val required: Boolean                  = true
  val additionalProperties: Boolean      = false
  val default: Option[T]                 = None
  val description: Option[String]        = None
}

case class FreeFormSchema[T](className: String) extends Schema[T] {
  val `type`: SchemaType                 = SchemaType.ObjectType
  val format: Option[String]             = None
  val properties: Map[String, Schema[?]] = Map.empty
  val items: Option[Schema[?]]           = None
  val `enum`: Option[Set[String]]        = None
  val required: Boolean                  = true
  val additionalProperties: Boolean      = true
  val default: Option[T]                 = None
  val description: Option[String]        = None
}
