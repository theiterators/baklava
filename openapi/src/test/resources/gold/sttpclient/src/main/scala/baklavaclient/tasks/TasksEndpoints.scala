package baklavaclient.tasks

import sttp.client4._
import sttp.client4.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import sttp.model.Uri

object TasksEndpoints {

  /** List tasks — List all tasks in a project */
  def listTasks(
      projectId: Long,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[ResponseException[String], Seq[Task]]] = {
    basicRequest
      .get(baseUri.addPath("projects", s"$projectId", "tasks"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .response(asJson[Seq[Task]])
  }

  /** Create task — Create a task in a project */
  def createTask(
      projectId: Long,
      body: CreateTaskRequest,
      oauth2Token: String,
      baseUri: Uri
  ): Request[Either[ResponseException[String], Task]] = {
    basicRequest
      .post(baseUri.addPath("projects", s"$projectId", "tasks"))
      .header("Authorization", s"Bearer ${oauth2Token}")
      .body(body.asJson.noSpaces)
      .contentType("application/json")
      .response(asJson[Task])
  }
}
