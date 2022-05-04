package pl.iterators.baklava.fetcher

import org.scalatest.TestSuite
import pl.iterators.baklava.core.fetchers.RouteBaklavaSpec

trait ScalatestRouteBaklavaSpec {
  thisTestSuite: TestSuite with RouteBaklavaSpec =>

  val suite = thisTestSuite
}
