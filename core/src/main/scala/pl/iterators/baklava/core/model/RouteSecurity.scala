package pl.iterators.baklava.core.model

sealed trait RouteSecurity

object RouteSecurity {
  case object Bearer extends RouteSecurity
  case object Basic  extends RouteSecurity
}
