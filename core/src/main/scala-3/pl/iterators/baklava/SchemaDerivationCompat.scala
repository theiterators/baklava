package pl.iterators.baklava

import scala.deriving.Mirror

trait SchemaDerivationCompat { this: SchemaDerivation =>
  inline def derived[T]: Schema[T] = ${ internal.SchemaMacros.derived[T]('{ this }) }

  inline given autoDerived[T](using Mirror.ProductOf[T]): Schema[T] = ${ internal.SchemaMacros.autoDerived[T]('{ this }) }
}
