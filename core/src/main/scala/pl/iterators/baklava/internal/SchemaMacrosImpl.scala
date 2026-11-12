package pl.iterators.baklava
package internal

import hearth.*
import hearth.fp.effect.*

/** Cross-compiled (Scala 2.13 / 3) `Schema` derivation, built with Hearth.
  *
  * One expansion walks the whole type tree: every case class / sealed trait it meets becomes one `lazy val` in the generated block, so a
  * type referenced from many fields is derived once, not once per occurrence. The generated code only collects metadata (field names,
  * defaults, subtype names) and hands it to the plain [[SchemaDerivation.deriveProduct]] / [[SchemaDerivation.deriveEnum]] methods of the
  * derivation object the macro was called on, so the naming hooks keep working as ordinary overrides.
  *
  * For each nested type, in order:
  *   1. an instance already in implicit scope (pinned instances, `SchemaDefaults`, integrations), never the automatic instance
  *      re-expanding itself;
  *   1. the built-in wrappers (`Option`, `List`, `Seq`, `Vector`, `Set`, `Array`, `Map`) around a derived type;
  *   1. structural derivation of case classes, case objects and sealed traits / enums;
  *   1. as a last resort, any instance at all, including the automatic one (e.g. a custom `Wrapper[T]` instance that needs `Schema[Dto]`).
  */
private[baklava] trait SchemaMacrosImpl { this: MacroCommons =>

  import SchemaMacrosImpl.*

  def deriveSchema[A: Type](self: Expr[SchemaDerivation], automatic: Boolean): Expr[Schema[A]] = {
    val program: MIO[Expr[Schema[A]]] =
      if (automatic && NestedLookup.active)
        // inside another derivation's lookup of an existing instance: decline, so that lookup only finds instances
        // that exist already and the enclosing derivation derives this type itself (and caches it)
        MIO.fail(Declined)
      else if (automatic && !autoDerivationEnabled)
        MIO.fail(AutoDerivationDisabled(Type.plainPrint[A]))
      else
        MIO.scoped { runSafe =>
          val ctx = new Ctx(self, ValDefsCache.mlocal)
          val top = runSafe(deriveStructurally[A](ctx)).getOrElse(runSafe(MIO.fail(NotDerivable(Type.plainPrint[A]))))
          runSafe(ctx.cache.get).toValDefs.use(_ => top)
        }

    // Not `runToExprOrFail`: that sets up run-wide state which must not be re-entered, and a nested type's instance
    // (e.g. `Wrapper[Dto]` needing `Schema[Dto]`) can expand this macro again while this run is still going.
    program.unsafe.runSync._2 match {
      case Right(expr)  => expr
      case Left(errors) => Environment.reportErrorAndAbort(errors.toVector.map(describe(Type.plainPrint[A], _)).mkString("\n"))
    }
  }

  private def describe(typeName: String, error: Throwable): String = error match {
    case Declined                        => s"Schema[$typeName] is derived by the enclosing derivation"
    case AutoDerivationDisabled(derived) =>
      s"automatic Schema derivation is disabled (-Xmacro-settings:baklava.schema.autoDerivation=false); " +
      s"define an instance for $derived, e.g. `implicit val schema: Schema[$derived] = Schema.derived`"
    case NotDerivable(missing) =>
      s"cannot derive Schema[$missing]: it is not a case class, case object, sealed trait or enum, " +
      s"and no Schema[$missing] is in scope"
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

  /** An instance that exists without deriving `A`: never the automatic instance expanding itself again. The automatic instance is left out
    * of the search where the compiler supports it, and declines anywhere deeper in the search (e.g. `listSchema`'s argument when looking
    * up `Schema[List[Dto]]`), which exclusion alone does not reach.
    */
  private def existingInstance[A](implicit SchemaA: Type[Schema[A]]): Option[Expr[Schema[A]]] =
    NestedLookup.during {
      try Expr.summonImplicitIgnoring[Schema[A]](automaticInstances*).toOption
      catch {
        // compilers before Scala 3.7 / 2.13.17 cannot leave implicits out of a search
        case _: HearthRequirementError => Expr.summonImplicit[Schema[A]].toOption
      }
    }

  private lazy val automaticInstances: Seq[UntypedMethod] =
    Type
      .of[SchemaDerivationCompat]
      .asUntyped
      .methods
      .filter(method => method.name == "autoDerived" || method.name == "gen")
      .toSeq

  /** Case classes, case objects and sealed traits / enums, each emitted once as a cached `lazy val`. */
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
      case None =>
        Type.exhaustiveChildren[A] match {
          case Some(children) =>
            // sorted, as magnolia listed subtypes: the enum ends up in a Set, and small Sets keep insertion order
            val names = children.toList.map { case (_, child) => child.Underlying.shortName }.sorted
            cached {
              MIO.pure(Expr.quote {
                Expr.splice(ctx.self).deriveEnum[A](Expr.splice(className), Expr.splice(stringListExpr(names)))
              })
            }
          case None => MIO.pure(None)
        }
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

  /** A platform-specific reference to the default of `A`'s `index`-th (0-based) constructor parameter; `None` uses Hearth's default-value
    * method.
    */
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

  /** `Option[T]`, `List[T]`, … around a type that needs deriving: derive `T` here (cached) and wrap it. */
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

  // Quoted code spells out _root_-qualified names: Scala 2 cross-quotes keep names as written, and the code lands in
  // the user's scope.
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

  private def stringListExpr(values: List[String]): Expr[List[String]] =
    values.foldRight(Expr.quote(_root_.scala.List.empty[_root_.java.lang.String])) { (value, acc) =>
      Expr.quote(Expr.splice(Expr(value)) :: Expr.splice(acc))
    }

  private def cacheKey[A: Type]: String = s"schema:${Type.plainPrint[A]}"

  private def autoDerivationEnabled: Boolean =
    (for {
      settings <- Environment.typedSettings.toOption
      baklava  <- settings.get("baklava")
      schema   <- baklava.get("schema")
      enabled  <- schema.get("autoDerivation").flatMap(_.asBoolean)
    } yield enabled).getOrElse(true)

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

  /** Set while a derivation looks up an existing instance on a compiler that cannot leave the automatic instance out of the search (before
    * Scala 3.7 / 2.13.17); the automatic instance declines while it is set.
    */
  object NestedLookup {
    // Scala 2 loads macro implementations in a fresh classloader per expansion by default
    // (-Ycache-macro-class-loader:none), so a plain field here would not be shared with the nested expansion. The
    // ThreadLocal lives in a JVM-wide slot and holds only bootstrap classes, so every classloader sees the same one.
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

  case object Declined                                      extends Exception with scala.util.control.NoStackTrace
  final case class AutoDerivationDisabled(typeName: String) extends Exception with scala.util.control.NoStackTrace
  final case class NotDerivable(typeName: String)           extends Exception with scala.util.control.NoStackTrace
}
