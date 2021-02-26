package pl.iterators.baklava.akkahttpscalatest

import org.scalatest.TestSuite
import pl.iterators.baklava.akkahttp.LibraryAkkaHttpBaseRouteDocSpec

trait LibraryAkkaHttpScalatestRouteDocSpec extends LibraryAkkaHttpBaseRouteDocSpec {
  thisTestSuite: TestSuite =>

  val suite = thisTestSuite
}
