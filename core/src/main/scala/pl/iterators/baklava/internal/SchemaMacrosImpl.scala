package pl.iterators.baklava
package internal

import hearth.*
import hearth.fp.effect.*

// One expansion derives the whole case class tree; each nested case class becomes one cached lazy val, so a type
// referenced from many fields is derived once. Nested types resolve in order: an instance already in scope, a built-in
// wrapper (Option, List, ...) around a derived type, structural derivation, then any instance at all.
private[baklava] trait SchemaMacrosImpl { this: MacroCommons =>

  import SchemaMacrosImpl.*

  def deriveSchema[A: Type](self: Expr[SchemaDerivation], automatic: Boolean): Expr[Schema[A]] = {
    val program: MIO[Expr[Schema[A]]] =
      if (automatic && NestedLookup.active) MIO.fail(Declined)
      else
        MIO.scoped { runSafe =>
          val ctx = new Ctx(self, ValDefsCache.mlocal)
          val top = runSafe(deriveStructurally[A](ctx)).getOrElse(runSafe(MIO.fail(NotDerivable(Type.plainPrint[A]))))
          runSafe(ctx.cache.get).toValDefs.use(_ => top)
        }

    // not runToExprOrFail: a nested type's instance (e.g. Wrapper[Dto]) can expand this macro again while this run is
    // still going, and runToExprOrFail must not be re-entered
    program.unsafe.runSync._2 match {
      case Right(expr)  => expr
      case Left(errors) => Environment.reportErrorAndAbort(errors.toVector.map(describe(Type.plainPrint[A], _)).mkString("\n"))
    }
  }

  private def describe(typeName: String, error: Throwable): String = error match {
    case Declined              => s"Schema[$typeName] is derived by the enclosing derivation"
    case NotDerivable(missing) =>
      s"cannot derive Schema[$missing]: only case classes and case objects are derived, and no Schema[$missing] is in scope"
    case other => s"cannot derive Schema[$typeName]: $other"
  }

  final private class Ctx(val self: Expr[SchemaDerivation], val cache: MLocal[ValDefsCache])

  private def deriveNested[A: Type](ctx: Ctx): MIO[Expr[Schema[A]]] = {
    implicit val SchemaA: Type[Schema[A]] = Types.Schema[A]
    ctx.cache.get0Ary[Schema[A]](cacheKey[A]).flatMap {
      case Some(cached) => MIO.pure(cached)
      case None         =>
        existingInstance[A] match {
          case Some(existing) => MIO.pure(existing)
          case None           =>
            deriveWrapped[A](ctx).flatMap {
              case Some(wrapped) => MIO.pure(wrapped)
              case None          =>
                deriveStructurally[A](ctx).flatMap {
                  case Some(derived) => MIO.pure(derived)
                  case None          =>
                    Expr.summonImplicit[Schema[A]].toOption match {
                      case Some(any) => MIO.pure(any)
                      case None      => MIO.fail(NotDerivable(Type.plainPrint[A]))
                    }
                }
            }
        }
    }
  }

  // the automatic instance declines while NestedLookup is set, so this finds only instances that already exist
  private def existingInstance[A](implicit SchemaA: Type[Schema[A]]): Option[Expr[Schema[A]]] =
    NestedLookup.during(Expr.summonImplicit[Schema[A]].toOption)

  private def deriveStructurally[A: Type](ctx: Ctx): MIO[Option[Expr[Schema[A]]]] = {
    implicit val SchemaA: Type[Schema[A]] = Types.Schema[A]
    val className                         = Expr(Type.shortName[A])

    def cached(body: => MIO[Expr[Schema[A]]]): MIO[Option[Expr[Schema[A]]]] = {
      val key     = cacheKey[A]
      val builder = ValDefBuilder.ofLazy[Schema[A]]("schema")
      for {
        _   <- ctx.cache.forwardDeclare(key, builder)
        b   <- body
        _   <- ctx.cache.buildCachedWith(key, builder)(_ => b)
        ref <- ctx.cache.get0Ary[Schema[A]](key)
      } yield ref
    }

    CaseClass.parse[A].toOption match {
      case Some(caseClass) =>
        cached {
          val params = caseClass.primaryConstructor.parameters.flatMap(_.values).filterNot(_.isImplicit)
          params
            .foldLeft(MIO.pure(List.empty[Expr[SchemaDerivation.Field]])) { (acc, param) =>
              acc.flatMap(fields => deriveField[A](ctx, param).map(_ :: fields))
            }
            .map(fields => productExpr[A](ctx.self, className, fields.reverse))
        }
      case None if Type.isCaseObject[A] || Type.isCaseVal[A] =>
        cached(MIO.pure(productExpr[A](ctx.self, className, Nil)))
      case None => MIO.pure(None)
    }
  }

  private def deriveField[A: Type](ctx: Ctx, param: Parameter): MIO[Expr[SchemaDerivation.Field]] = {
    import param.tpe.Underlying as F
    implicit val AnyT: Type[Any] = Types.Any
    deriveNested[F](ctx).map { schema =>
      val defaultValue =
        if (param.hasDefault) platformDefaultValue[A](param.index).orElse(hearthDefaultValue(param)) else None
      val default: Expr[Option[Any]] = defaultValue match {
        case Some(value) => Expr.quote(_root_.scala.Some(Expr.splice(value)))
        case None        => Expr.quote(_root_.scala.None)
      }
      Expr.quote {
        new _root_.pl.iterators.baklava.SchemaDerivation.Field(
          Expr.splice(Expr(param.name)),
          Expr.splice(schema),
          Expr.splice(default)
        )
      }
    }
  }

  // overridden on Scala 2, where Hearth's default getter does not typecheck inside implicit search
  protected def platformDefaultValue[A: Type](index: Int): Option[Expr[Any]] = None

  private def hearthDefaultValue(param: Parameter)(implicit AnyT: Type[Any]): Option[Expr[Any]] =
    param.defaultValue.flatMap { method =>
      type EitherString[X] = Either[String, X]
      method
        .foldF[EitherString](
          onInstance = _ => Left("default value getters need no instance"),
          onTypes = _ => Right(Map.empty),
          onValues = _ => Right(Map.empty)
        )
        .flatten
        .toOption
        .map { value =>
          import value.Underlying
          value.value.upcast[Any]
        }
    }

  private def deriveWrapped[A: Type](ctx: Ctx): MIO[Option[Expr[Schema[A]]]] = {
    def wrap1[F[_]](ctor: Type.Ctor1[F])(build: Wrap1[F]): Option[MIO[Expr[Schema[A]]]] =
      ctor.unapply(Type[A]).collect {
        case inner if Type[A] =:= ctor[inner.Underlying](using inner.Underlying) =>
          import inner.Underlying as T
          deriveNested[T](ctx).map(schema => build[T](schema).asInstanceOf[Expr[Schema[A]]])
      }

    val candidate: Option[MIO[Expr[Schema[A]]]] =
      wrap1(Types.Option)(new Wrap1[Option] {
        def apply[T: Type](s: Expr[Schema[T]]) =
          Expr.quote(_root_.pl.iterators.baklava.Schema.optionSchema[T](Expr.splice(s)))
      })
        .orElse(wrap1(Types.List)(new Wrap1[List] {
          def apply[T: Type](s: Expr[Schema[T]]) =
            Expr.quote(_root_.pl.iterators.baklava.Schema.listSchema[T](Expr.splice(s)))
        }))
        .orElse(wrap1(Types.Vector)(new Wrap1[Vector] {
          def apply[T: Type](s: Expr[Schema[T]]) =
            Expr.quote(_root_.pl.iterators.baklava.Schema.vectorSchema[T](Expr.splice(s)))
        }))
        .orElse(wrap1(Types.Seq)(new Wrap1[scala.collection.immutable.Seq] {
          def apply[T: Type](s: Expr[Schema[T]]) =
            Expr.quote(_root_.pl.iterators.baklava.Schema.seqSchema[T](Expr.splice(s)))
        }))
        .orElse(wrap1(Types.Set)(new Wrap1[scala.collection.immutable.Set] {
          def apply[T: Type](s: Expr[Schema[T]]) =
            Expr.quote(_root_.pl.iterators.baklava.Schema.setSchema[T](Expr.splice(s)))
        }))
        .orElse(wrap1(Types.Array)(new Wrap1[Array] {
          def apply[T: Type](s: Expr[Schema[T]]) =
            Expr.quote(_root_.pl.iterators.baklava.Schema.arraySchema[T](Expr.splice(s)))
        }))
        .orElse {
          Types.Map.unapply(Type[A]).collect {
            case (key, value) if Type[A] =:= Types.Map[key.Underlying, value.Underlying](using key.Underlying, value.Underlying) =>
              import key.Underlying as K
              import value.Underlying as V
              for {
                k <- deriveNested[K](ctx)
                v <- deriveNested[V](ctx)
              } yield Expr
                .quote(_root_.pl.iterators.baklava.Schema.mapSchema[K, V](Expr.splice(k), Expr.splice(v)))
                .asInstanceOf[Expr[Schema[A]]]
          }
        }

    candidate match {
      case Some(mio) => mio.map(Some(_))
      case None      => MIO.pure(None)
    }
  }

  private trait Wrap1[F[_]] {
    def apply[T: Type](schema: Expr[Schema[T]]): Expr[Schema[F[T]]]
  }

  // _root_-qualified: Scala 2 cross-quotes keep names as written and the code lands in the user's scope
  private def productExpr[A: Type](
      self: Expr[SchemaDerivation],
      className: Expr[String],
      fields: List[Expr[SchemaDerivation.Field]]
  ): Expr[Schema[A]] = {
    val list = fields.foldRight(Expr.quote(_root_.scala.List.empty[_root_.pl.iterators.baklava.SchemaDerivation.Field])) { (field, acc) =>
      Expr.quote(Expr.splice(field) :: Expr.splice(acc))
    }
    Expr.quote(Expr.splice(self).deriveProduct[A](Expr.splice(className), Expr.splice(list)))
  }

  private def cacheKey[A: Type]: String = s"schema:${Type.plainPrint[A]}"

  private object Types {
    val Any: Type[Any]                                  = Type.of[Any]
    val Schema: Type.Ctor1[pl.iterators.baklava.Schema] = Type.Ctor1.of[pl.iterators.baklava.Schema]
    val Option: Type.Ctor1[scala.Option]                = Type.Ctor1.of[scala.Option]
    val List: Type.Ctor1[scala.List]                    = Type.Ctor1.of[scala.List]
    val Vector: Type.Ctor1[scala.Vector]                = Type.Ctor1.of[scala.Vector]
    val Seq: Type.Ctor1[scala.collection.immutable.Seq] = Type.Ctor1.of[scala.collection.immutable.Seq]
    val Set: Type.Ctor1[scala.collection.immutable.Set] = Type.Ctor1.of[scala.collection.immutable.Set]
    val Array: Type.Ctor1[scala.Array]                  = Type.Ctor1.of[scala.Array]
    val Map: Type.Ctor2[scala.collection.immutable.Map] = Type.Ctor2.of[scala.collection.immutable.Map]
  }
}

private[baklava] object SchemaMacrosImpl {

  // Set while a derivation looks up an existing instance; the automatic instance declines meanwhile. Kept in a JVM-wide
  // slot because Scala 2 loads macro implementations in a fresh classloader per expansion.
  object NestedLookup {
    private val flag: ThreadLocal[java.lang.Boolean] = {
      val props = System.getProperties
      props.synchronized {
        props.get(SlotKey) match {
          case existing: ThreadLocal[?] => existing.asInstanceOf[ThreadLocal[java.lang.Boolean]]
          case _                        =>
            val created = new ThreadLocal[java.lang.Boolean]
            props.put(SlotKey, created)
            created
        }
      }
    }
    final private val SlotKey = "pl.iterators.baklava.internal.SchemaMacros.nestedLookup"

    def active: Boolean = java.lang.Boolean.TRUE == flag.get()

    def during[T](thunk: => T): T = {
      val previous = flag.get()
      flag.set(java.lang.Boolean.TRUE)
      try thunk
      finally flag.set(previous)
    }
  }

  case object Declined                            extends Exception with scala.util.control.NoStackTrace
  final case class NotDerivable(typeName: String) extends Exception with scala.util.control.NoStackTrace
}
