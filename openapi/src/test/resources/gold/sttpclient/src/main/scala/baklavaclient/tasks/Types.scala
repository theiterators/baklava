package baklavaclient.tasks

final case class CreateTaskRequest(description: Option[String] = None, priority: String, title: String)

final case class Task(description: Option[String] = None, done: Boolean, id: Long, priority: String, title: String)
