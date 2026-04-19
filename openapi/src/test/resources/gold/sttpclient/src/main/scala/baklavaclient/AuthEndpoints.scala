package baklavaclient

import sttp.client4.*
import sttp.model.Uri

object AuthEndpoints {

  /** Login — Exchange HTTP Basic credentials for a JWT token */
  def login(
      bodyJson: String,
      basicAuthUsername: String,
      basicAuthPassword: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("auth", "login"))
      .auth.basic(basicAuthUsername, basicAuthPassword)
      .body(bodyJson)
      .contentType("application/json")
  }

  /** Who am I — Return the profile of the currently authenticated user */
  def me(
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("me"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }
}
