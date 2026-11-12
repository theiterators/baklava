package pl.iterators.baklava

import scala.deriving.Mirror

trait SchemaDerivationCompat { this: SchemaDerivation =>

  /** Derives a `Schema[T]` for a case class or sealed trait / enum; also backs `derives Schema`. */
  inline def derived[T]: Schema[T] = ${ internal.SchemaMacros.derived[T]('{ this }) }

  /** Automatic instance for case classes and sealed traits / enums with no `Schema` in scope. */
  inline given autoDerived[T](using Mirror.Of[T]): Schema[T] = ${ internal.SchemaMacros.autoDerived[T]('{ this }) }
}
