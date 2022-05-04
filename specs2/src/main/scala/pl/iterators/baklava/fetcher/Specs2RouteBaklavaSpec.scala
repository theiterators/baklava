package pl.iterators.baklava.fetcher

import org.specs2.mutable.SpecificationLike
import pl.iterators.baklava.core.fetchers.RouteBaklavaSpec

trait Specs2RouteBaklavaSpec {
  thisTestSuite: SpecificationLike with RouteBaklavaSpec =>

  val suite = thisTestSuite
}
