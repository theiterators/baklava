package baklavaclient.projects

import sttp.client4.*
import sttp.model.Uri
import baklavaclient.common.ErrorResponse

object ProjectsEndpoints {

  /** List projects — List projects, optionally filtered by status */
  def listProjects(
      status: Option[String] = None,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("projects")        .addParam(Option.when(status.isDefined)("status" -> status.get.toString)))
      .header("Authorization", s"Bearer ${oauth2Token}")
  }

  /** Create project — Create a new project */
  def createProject(
      bodyJson: String,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("projects"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(bodyJson)
      .contentType("application/json")
  }

  /** Patch project — Partially update a project */
  def patchProject(
      projectId: Long,
      bodyJson: String,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .patch(baseUri.addPath("projects", "$projectId"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(bodyJson)
      .contentType("application/json")
  }
}
