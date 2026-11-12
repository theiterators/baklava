package pl.iterators.baklava
package internal

import scala.quoted.*

private[baklava] class SchemaMacros(q: Quotes) extends hearth.MacroCommonsScala3(using q), SchemaMacrosImpl

private[baklava] object SchemaMacros {

  def derived[T: Type](self: Expr[SchemaDerivation])(using q: Quotes): Expr[Schema[T]] =
    new SchemaMacros(q).deriveSchema[T](self, automatic = false)

  def autoDerived[T: Type](self: Expr[SchemaDerivation])(using q: Quotes): Expr[Schema[T]] =
    new SchemaMacros(q).deriveSchema[T](self, automatic = true)
}
