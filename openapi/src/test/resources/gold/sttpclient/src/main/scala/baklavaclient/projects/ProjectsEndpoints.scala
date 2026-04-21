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
      status: Option[String] = None,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[ResponseException[String], Seq[Project]]] = {
    basicRequest
      .get(baseUri.addPath("projects")        .addParam("status", status.map(_.toString)))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .response(asJson[Seq[Project]])
  }

  /** Create project — Create a new project */
  def createProject(
      body: CreateProjectRequest,
      oauth2Token: String,
      baseUri: Uri
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
      projectId: Long,
      body: PatchProjectRequest,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[ResponseException[String], Project]] = {
    basicRequest
      .patch(baseUri.addPath("projects", s"$projectId"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(body.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[Project])
  }
}
