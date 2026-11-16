package pl.iterators.baklava

trait SchemaDerivationCompat { this: SchemaDerivation =>
  def derived[T]: Schema[T] = macro internal.SchemaMacros.derivedImpl[T]

  implicit def gen[T]: Schema[T] = macro internal.SchemaMacros.genImpl[T]
}
