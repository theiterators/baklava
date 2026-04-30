package baklavaclient.projects

final case class CreateProjectRequest(description: Option[String] = None, name: String, status: String)

final case class PatchProjectRequest(description: Option[String] = None, name: Option[String] = None, status: Option[String] = None)

final case class Project(createdAt: String, description: Option[String] = None, id: Long, name: String, ownerId: java.util.UUID, status: String)
