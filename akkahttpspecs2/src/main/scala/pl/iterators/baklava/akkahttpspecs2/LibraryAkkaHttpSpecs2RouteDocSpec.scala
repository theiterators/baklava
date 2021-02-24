package pl.iterators.baklava.akkahttpspecs2

import akka.http.scaladsl.testkit.Specs2RouteTest
import org.specs2.mutable.SpecificationLike
import pl.iterators.baklava.akkahttp.LibraryAkkaHttpBaseRouteDocSpec

trait LibraryAkkaHttpSpecs2RouteDocSpec
    extends LibraryAkkaHttpBaseRouteDocSpec
    with Specs2RouteTest
    with SpecificationLike
