package baklavaclient.admin

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import sttp.model.Uri
import baklavaclient.common.HealthResponse

object AdminEndpoints {

  /** Get config — Read the effective runtime configuration */
  def adminGetConfig(
      baseUri: Uri,
      basicAuthUsername: String,
      basicAuthPassword: String
  ): Request[Either[ResponseException[String], HealthResponse]] = {
    basicRequest
      .get(baseUri.addPath("admin", "config"))
      .auth.basic(basicAuthUsername, basicAuthPassword)
      .response(asJson[HealthResponse])
  }

  /** Get logger level — Read a logger's effective level */
  def adminGetLogger(
      baseUri: Uri,
      basicAuthUsername: String,
      basicAuthPassword: String
  )(
      name: String
  ): Request[Either[ResponseException[String], HealthResponse]] = {
    basicRequest
      .get(baseUri.addPath("admin", "loggers", s"$name"))
      .auth.basic(basicAuthUsername, basicAuthPassword)
      .response(asJson[HealthResponse])
  }
}
