package pl.iterators.baklava
package internal

import scala.reflect.macros.whitebox

// whitebox like magnolia: blackbox implicit macros fail when expanded inside another derivation's implicit search
private[baklava] class SchemaMacros(val c: whitebox.Context) extends hearth.MacroCommonsScala2 with SchemaMacrosImpl {

  def derivedImpl[T: c.WeakTypeTag]: c.Expr[Schema[T]] =
    resettle(deriveSchema[T](c.Expr[SchemaDerivation](c.prefix.tree), automatic = false))

  def genImpl[T: c.WeakTypeTag]: c.Expr[Schema[T]] =
    resettle(deriveSchema[T](c.Expr[SchemaDerivation](c.prefix.tree), automatic = true))

  // the cached lazy vals get the wrong owner when expanded inside another derivation's implicit search
  private def resettle[A](expr: c.Expr[A]): c.Expr[A] = c.Expr[A](c.untypecheck(expr.tree))

  // Hearth's default getter carries a symbol that types as <error> inside implicit search; select it by name instead
  override protected def platformDefaultValue[A: Type](index: Int): Option[Expr[Any]] = {
    import c.universe.*
    val tpe       = Type[A].tpe.dealias
    val companion = tpe.typeSymbol.companion
    if (companion == NoSymbol) None
    else {
      val prefix = tpe match {
        case TypeRef(pre, _, _) => pre
        case _                  => NoPrefix
      }
      val companionRef = internal.gen.mkAttributedRef(prefix, companion)
      Some(c.Expr[Any](q"$companionRef.${TermName("$lessinit$greater$default$" + (index + 1))}"))
    }
  }
}
