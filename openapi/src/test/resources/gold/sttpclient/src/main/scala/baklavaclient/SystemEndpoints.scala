package baklavaclient

import sttp.client4.*
import sttp.model.Uri

object SystemEndpoints {

  /** Liveness probe — Return service liveness — no authentication required */
  def health(
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("health"))
  }
}
