package pl.iterators.baklava

trait SchemaDerivationCompat { this: SchemaDerivation =>

  /** Derives a `Schema[T]` for a case class or sealed trait. */
  def derived[T]: Schema[T] = macro internal.SchemaMacros.derivedImpl[T]

  /** Automatic instance for case classes and sealed traits with no `Schema` in scope. */
  implicit def gen[T]: Schema[T] = macro internal.SchemaMacros.genImpl[T]
}
