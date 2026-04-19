package baklavaclient

final case class CreateProjectRequest(description: Option[String] = None, name: String, status: String)

final case class CreateTaskRequest(description: Option[String] = None, priority: String, title: String)

final case class ErrorResponse(code: String, details: Option[Seq[String]] = None, message: String)

final case class HealthResponse(status: String, uptimeSeconds: Long)

final case class LoginForm(client_id: String, grant_type: String)

final case class LoginResponse(token: String, user: User)

final case class PaginatedUsers(limit: Int, page: Int, total: Int, users: Seq[User])

final case class PatchProjectRequest(description: Option[String] = None, name: Option[String] = None, status: Option[String] = None)

final case class Project(createdAt: String, description: Option[String] = None, id: Long, name: String, ownerId: java.util.UUID, status: String)

final case class Task(description: Option[String] = None, done: Boolean, id: Long, priority: String, title: String)

final case class UpdateUserRequest(name: String, role: String)

final case class User(email: String, id: java.util.UUID, name: String, role: String)

final case class WebhookAck(received: Boolean)

final case class WebhookPayload(data: String, event: String)
