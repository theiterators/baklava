package pl.iterators.baklava.core.model

import scala.language.implicitConversions

case class RouteSecurityGroup(list: List[RouteSecurity]) {
  override def toString: String = list.mkString(", ")
}

object RouteSecurityGroup {

  implicit def routeSecurityToRouteSecurityGroup(security: RouteSecurity): RouteSecurityGroup =
    RouteSecurityGroup(List(security))

  implicit def routeSecurityListToRouteSecurityGroup(securityList: List[RouteSecurity]): RouteSecurityGroup =
    RouteSecurityGroup(securityList)

}
