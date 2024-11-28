package pl.iterators.baklava.specs2

import org.specs2.specification.core.{AsExecution, Fragment, Fragments}
import org.specs2.specification.dsl.mutable.{BlockDsl, ExampleDsl}
import pl.iterators.baklava.{Baklava2Context, BaklavaHttpDsl, BaklavaTestFrameworkDsl}

trait BaklavaSpecs2[RouteType, ToRequestBodyType[_], FromResponseBodyType[_]]
    extends BaklavaTestFrameworkDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Fragment, Fragments, AsExecution]
    with BlockDsl
    with ExampleDsl {
  this: BaklavaHttpDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Fragment, Fragments, AsExecution] =>

  override def fragmentsFromSeq(fragments: Seq[Fragment]): Fragments = Fragments(fragments*)

  override def concatFragments(fragments: Seq[Fragments]): Fragments = fragments.reduce(_.append(_))

  override def pathLevelTextWithFragments(text: String, context: Baklava2Context[?, ?, ?, ?, ?], fragments: => Fragments): Fragments =
    addFragmentsBlockWithText(text, fragments)

  override def methodLevelTextWithFragments(text: String, context: Baklava2Context[?, ?, ?, ?, ?], fragments: => Fragments): Fragments =
    addFragmentsBlockWithText(text, fragments)

  override def requestLevelTextWithExecution[R: AsExecution](text: String, context: Baklava2Context[?, ?, ?, ?, ?], r: => R): Fragment =
    text >> r
}
