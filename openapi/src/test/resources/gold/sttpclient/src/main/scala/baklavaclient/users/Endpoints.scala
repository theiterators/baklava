package baklavaclient.users

import sttp.client4.*
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
      .get(baseUri.addPath("users")        .addParam(Option.when(page.isDefined)("page" -> page.get.toString))
        .addParam(Option.when(limit.isDefined)("limit" -> limit.get.toString))
        .addParam(Option.when(role.isDefined)("role" -> role.get.toString)))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }

  /** Delete user — Delete a user */
  def deleteUser(
      userId: java.util.UUID,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .delete(baseUri.addPath("users", "$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }

  /** Get user — Fetch a single user by UUID */
  def getUser(
      userId: java.util.UUID,
      bearerAuthToken: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("users", "$userId"))
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
      .put(baseUri.addPath("users", "$userId"))
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
      .post(baseUri.addPath("users", "$userId", "photo"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .body(bodyJson)
      .contentType("application/json")
  }
}
