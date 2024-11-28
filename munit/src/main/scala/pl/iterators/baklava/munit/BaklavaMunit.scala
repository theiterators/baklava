package pl.iterators.baklava.munit

import munit.FunSuite
import pl.iterators.baklava.{Baklava2Context, BaklavaHttpDsl, BaklavaTestFrameworkDsl}

trait BaklavaMunit[RouteType, ToRequestBodyType[_], FromResponseBodyType[_]]
    extends FunSuite
    with BaklavaTestFrameworkDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, MunitAsExecution] {
  this: BaklavaHttpDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, MunitAsExecution] =>

  override def fragmentsFromSeq(fragments: Seq[Unit]): Unit = fragments.foreach(identity)

  override def concatFragments(fragments: Seq[Unit]): Unit = fragments.foreach(identity)

  override def pathLevelTextWithFragments(text: String, context: Baklava2Context[?, ?, ?, ?, ?], fragments: => Unit): Unit = fragments

  override def methodLevelTextWithFragments(text: String, context: Baklava2Context[?, ?, ?, ?, ?], fragments: => Unit): Unit = fragments

  override def requestLevelTextWithExecution[R: MunitAsExecution](text: String, context: Baklava2Context[?, ?, ?, ?, ?], r: => R): Unit =
    test(s"${context.method.get.value} ${context.symbolicPath} should respond with -> " + text)(r)
}

trait MunitAsExecution[T]

object MunitAsExecution {
  implicit def munitAsExecution[T]: MunitAsExecution[T] = new MunitAsExecution[T] {}
}
