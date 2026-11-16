package pl.iterators.baklava

object SchemaNamingFixtures {
  case class InnerPayload(innerField: String, someCount: Int)
  case class OuterPayload(
      firstName: String,
      lastName: Option[String],
      innerPayload: InnerPayload,
      tagsList: Seq[InnerPayload],
      extraData: Map[String, InnerPayload]
  )

  case class WithDefault(pageSize: Int = 42)
}

object SnakeCaseSchemas extends SchemaDerivation with SchemaDefaults {
  override def transformMemberName(name: String): String = SchemaNameTransform.snakeCase(name)
}
