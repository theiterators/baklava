package baklavaclient.users

import sttp.client4._
import sttp.model.Uri
import baklavaclient.common.ErrorResponse
import baklavaclient.common.User

object UsersEndpoints {

  /** List users — List users with pagination and optional role filter */
  def listUsers(
      page: Option[Int] = None,
      limit: Option[Int] = None,
      role: Option[String] = None,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("users")        .addParam("page", page.map(_.toString))
        .addParam("limit", limit.map(_.toString))
        .addParam("role", role.map(_.toString)))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }

  /** Delete user — Delete a user */
  def deleteUser(
      userId: java.util.UUID,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .delete(baseUri.addPath("users", s"$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }

  /** Get user — Fetch a single user by UUID */
  def getUser(
      userId: java.util.UUID,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("users", s"$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }

  /** Update user — Replace a user's profile (admin only) */
  def updateUser(
      userId: java.util.UUID,
      bodyJson: String,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .put(baseUri.addPath("users", s"$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .body(bodyJson)
      .contentType("application/json")
  }

  /** Upload photo — Upload a profile photo alongside a caption as multipart/form-data */
  def uploadPhoto(
      userId: java.util.UUID,
      bodyJson: String,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("users", s"$userId", "photo"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .body(bodyJson)
      .contentType("application/json")
  }
}
