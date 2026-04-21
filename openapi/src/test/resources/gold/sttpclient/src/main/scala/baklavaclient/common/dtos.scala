package baklavaclient.common

final case class ErrorResponse(code: String, details: Option[Seq[String]] = None, message: String)

final case class User(email: String, id: java.util.UUID, name: String, role: String)
