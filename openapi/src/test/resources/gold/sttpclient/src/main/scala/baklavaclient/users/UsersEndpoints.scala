package baklavaclient.users

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.model.Uri
import baklavaclient.common.ErrorResponse
import baklavaclient.common.User

object UsersEndpoints {

  /** List users — List users with pagination and optional role filter */
  def listUsers(
      baseUri: Uri,
      bearerAuthToken: String,
      page: Option[Int] = None,
      limit: Option[Int] = None,
      role: Option[String] = None
  ): Request[Either[ResponseException[String], PaginatedUsers]] = {
    basicRequest
      .get(baseUri.addPath("users")        .addParam("page", page.map(_.toString))
        .addParam("limit", limit.map(_.toString))
        .addParam("role", role.map(_.toString)))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .response(asJson[PaginatedUsers])
  }

  /** Delete user — Delete a user */
  def deleteUser(
      baseUri: Uri,
      bearerAuthToken: String,
      userId: java.util.UUID
  ): Request[Either[String, String]] = {
    basicRequest
      .delete(baseUri.addPath("users", s"$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
  }

  /** Get user — Fetch a single user by UUID */
  def getUser(
      baseUri: Uri,
      bearerAuthToken: String,
      userId: java.util.UUID
  ): Request[Either[ResponseException[String], User]] = {
    basicRequest
      .get(baseUri.addPath("users", s"$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .response(asJson[User])
  }

  /** Update user — Replace a user's profile (admin only) */
  def updateUser(
      baseUri: Uri,
      bearerAuthToken: String,
      userId: java.util.UUID,
      body: UpdateUserRequest
  ): Request[Either[ResponseException[String], User]] = {
    basicRequest
      .put(baseUri.addPath("users", s"$userId"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .body(body.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[User])
  }

  /** Upload photo — Upload a profile photo alongside a caption as multipart/form-data */
  def uploadPhoto(
      baseUri: Uri,
      bearerAuthToken: String,
      userId: java.util.UUID,
      bodyJson: String
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("users", s"$userId", "photo"))
      .header("Authorization", s"Bearer ${bearerAuthToken}")
      .body(bodyJson)
      .contentType("multipart/form-data; boundary=baklava-multipart-boundary")
  }
}
