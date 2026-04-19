package baklavaclient

import sttp.client4.*
import sttp.model.Uri

object TasksEndpoints {

  /** List tasks — List all tasks in a project */
  def listTasks(
      projectId: Long,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .get(baseUri.addPath("projects", "$projectId", "tasks"))
      .header("Authorization", s"Bearer ${oauth2Token}")
  }

  /** Create task — Create a task in a project */
  def createTask(
      projectId: Long,
      bodyJson: String,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[String, String]] = {
    basicRequest
      .post(baseUri.addPath("projects", "$projectId", "tasks"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(bodyJson)
      .contentType("application/json")
  }
}
