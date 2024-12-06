package pl.iterators.baklava.munit

import munit.FunSuite
import pl.iterators.baklava.{Baklava2RequestContext, BaklavaHttpDsl, BaklavaTestFrameworkDsl}

trait BaklavaMunit[RouteType, ToRequestBodyType[_], FromResponseBodyType[_]]
    extends FunSuite
    with BaklavaTestFrameworkDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, MunitAsExecution] {
  this: BaklavaHttpDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, MunitAsExecution] =>

  override def fragmentsFromSeq(fragments: Seq[Unit]): Unit = fragments.foreach(identity)

  override def concatFragments(fragments: Seq[Unit]): Unit = fragments.foreach(identity)

  override def pathLevelTextWithFragments(text: String, context: Baklava2RequestContext[?, ?, ?, ?, ?, ?], fragments: => Unit): Unit =
    fragments

  override def methodLevelTextWithFragments(text: String, context: Baklava2RequestContext[?, ?, ?, ?, ?, ?], fragments: => Unit): Unit =
    fragments

  override def requestLevelTextWithExecution[R: MunitAsExecution](
      text: String,
      context: Baklava2RequestContext[?, ?, ?, ?, ?, ?],
      r: => R
  ): Unit =
    test(s"${context.method.get.value} ${context.symbolicPath} should respond with -> " + text)(r)
}

trait MunitAsExecution[T]

object MunitAsExecution {
  implicit def munitAsExecution[T]: MunitAsExecution[T] = new MunitAsExecution[T] {}
}
