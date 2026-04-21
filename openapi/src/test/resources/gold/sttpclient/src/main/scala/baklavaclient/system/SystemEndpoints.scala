package baklavaclient.system

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import sttp.model.Uri

object SystemEndpoints {

  /** Liveness probe — Return service liveness — no authentication required */
  def health(
      baseUri: Uri
  ): Request[Either[ResponseException[String], HealthResponse]] = {
    basicRequest
      .get(baseUri.addPath("health"))
      .response(asJson[HealthResponse])
  }
}
