package pl.iterators.baklava.scalatest

import org.scalatest.funspec.AnyFunSpecLike
import pl.iterators.baklava.{BaklavaHttpDsl, BaklavaTestFrameworkDsl}

trait BaklavaScalatest[RouteType, ToRequestBodyType[_], FromResponseBodyType[_]]
    extends BaklavaTestFrameworkDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, ScalatestAsExecution]
    with AnyFunSpecLike {
  this: BaklavaHttpDsl[RouteType, ToRequestBodyType, FromResponseBodyType, Unit, Unit, ScalatestAsExecution] =>

  override def fragmentsFromSeq(fragments: Seq[Unit]): Unit = fragments.foreach(identity)

  override def concatFragments(fragments: Seq[Unit]): Unit = fragments.foreach(identity)

  override def pathLevelTextWithFragments(text: String, fragments: => Unit): Unit = describe(text)(fragments)

  override def methodLevelTextWithFragments(text: String, fragments: => Unit): Unit = describe(text)(fragments)

  override def requestLevelTextWithExecution[R: ScalatestAsExecution](text: String, r: => R): Unit = it(text)(r)
}

trait ScalatestAsExecution[T]

object ScalatestAsExecution {
  implicit def scalatestAsExecution[T]: ScalatestAsExecution[T] = new ScalatestAsExecution[T] {}
}
