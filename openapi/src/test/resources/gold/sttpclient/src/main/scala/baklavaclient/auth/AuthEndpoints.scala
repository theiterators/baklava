package baklavaclient.auth

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import sttp.model.Uri
import baklavaclient.common.ErrorResponse
import baklavaclient.common.User

object AuthEndpoints {

  /** Login — Exchange HTTP Basic credentials for a JWT token */
  def login(
      baseUri: Uri,
      basicAuthUsername: String,
      basicAuthPassword: String,
      bodyJson: String
  ): Request[Either[ResponseException[String], LoginResponse]] = {
    basicRequest
      .post(baseUri.addPath("auth", "login"))
      .auth.basic(basicAuthUsername, basicAuthPassword)
      .body(bodyJson)
      .contentType("application/x-www-form-urlencoded")
      .response(asJson[LoginResponse])
  }

  /** Who am I — Return the profile of the currently authenticated user */
  def me(
      baseUri: Uri,
      bearerAuthToken: String
  ): Request[Either[ResponseException[String], User]] = {
    basicRequest
      .get(baseUri.addPath("me"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .response(asJson[User])
  }
}
