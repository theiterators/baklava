package pl.iterators.baklava
package internal

import scala.reflect.macros.whitebox

// whitebox, like magnolia's Scala 2 derivation
private[baklava] class SchemaMacros(val c: whitebox.Context) extends hearth.MacroCommonsScala2 with SchemaMacrosImpl {

  def derivedImpl[T: c.WeakTypeTag]: c.Expr[Schema[T]] =
    resettle(deriveSchema[T](c.Expr[SchemaDerivation](c.prefix.tree), automatic = false))

  def genImpl[T: c.WeakTypeTag]: c.Expr[Schema[T]] =
    resettle(deriveSchema[T](c.Expr[SchemaDerivation](c.prefix.tree), automatic = true))

  // The output defines local symbols (the cached lazy vals). When the expansion happens inside another derivation's
  // implicit lookup, attributed local symbols end up with the wrong owner and the instance silently fails to typecheck;
  // resetting them lets the typer re-enter them where the tree lands (magnolia does the same).
  private def resettle[A](expr: c.Expr[A]): c.Expr[A] = c.Expr[A](c.untypecheck(expr.tree))

  // Hearth hands back the default getter with its symbol attached; inside an implicit search that symbol's type can
  // come out erroneous and silently disqualify the whole instance. Select the getter by name on the companion instead
  // and let the typer resolve it, as magnolia does.
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
