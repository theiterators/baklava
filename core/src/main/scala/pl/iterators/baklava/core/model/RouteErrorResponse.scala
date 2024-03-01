package pl.iterators.baklava.core.model

final case class RouteErrorResponse[T](`result`: T, status: Int)(marshaller: T => String) {
  lazy val jsonData: RouteErrorResponseJsonData =
    RouteErrorResponseJsonData(marshaller(result), status)

  def resultName: String = result.getClass.getSimpleName.replaceAll("\\$", "")
}
final case class RouteErrorResponseJsonData(`type`: String, status: Int)
