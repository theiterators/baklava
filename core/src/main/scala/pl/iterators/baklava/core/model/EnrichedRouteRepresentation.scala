package pl.iterators.baklava.core.model

class EnrichedRouteRepresentation[Request, Response] private (
    val routeRepresentation: RouteRepresentation[Request, Response],
    val enrichDescriptions: Seq[EnrichedDescription]
)

object EnrichedRouteRepresentation {
  def apply[Request, Response](
      routeRepresentation: RouteRepresentation[Request, Response],
      descriptions: Seq[String]
  ): EnrichedRouteRepresentation[Request, Response] =
    new EnrichedRouteRepresentation(routeRepresentation, descriptions.map(EnrichedDescription.apply))
}

class EnrichedDescription private (val description: String, val statusCodeOpt: Option[Int])

object EnrichedDescription {

  def apply(description: String): EnrichedDescription =
    new EnrichedDescription(description, extractStatusCode(description))

  private def extractStatusCode(description: String): Option[Int] =
    description.toLowerCase
      .replace("inaccessible", "unauthorized")
      .split(" ")
      .flatMap(s => statusCodesMap.get(s))
      .headOption

  private val statusCodesMap =
    Map(
      "ok"                  -> 200,
      "created"             -> 201,
      "accepted"            -> 202,
      "nocontent"           -> 204,
      "badrequest"          -> 400,
      "inaccessible"        -> 401,
      "unauthorized"        -> 401,
      "forbidden"           -> 403,
      "notfound"            -> 404,
      "conflict"            -> 409,
      "internalerror"       -> 500,
      "internalservererror" -> 500
    )
}
