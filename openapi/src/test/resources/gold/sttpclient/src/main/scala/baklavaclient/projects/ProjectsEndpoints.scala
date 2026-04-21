package baklavaclient.projects

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.model.Uri
import baklavaclient.common.ErrorResponse

object ProjectsEndpoints {

  /** List projects — List projects, optionally filtered by status */
  def listProjects(
      baseUri: Uri,
      oauth2Token: String,
      status: Option[String] = None
  ): Request[Either[ResponseException[String], Seq[Project]]] = {
    basicRequest
      .get(baseUri.addPath("projects")        .addParam("status", status.map(_.toString)))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .response(asJson[Seq[Project]])
  }

  /** Create project — Create a new project */
  def createProject(
      baseUri: Uri,
      oauth2Token: String,
      body: CreateProjectRequest
  ): Request[Either[ResponseException[String], Project]] = {
    basicRequest
      .post(baseUri.addPath("projects"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(body.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[Project])
  }

  /** Patch project — Partially update a project */
  def patchProject(
      baseUri: Uri,
      oauth2Token: String,
      projectId: Long,
      body: PatchProjectRequest
  ): Request[Either[ResponseException[String], Project]] = {
    basicRequest
      .patch(baseUri.addPath("projects", s"$projectId"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(body.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[Project])
  }
}
