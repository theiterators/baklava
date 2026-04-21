package baklavaclient.users

import baklavaclient.common.User

final case class PaginatedUsers(limit: Int, page: Int, total: Int, users: Seq[User])

final case class UpdateUserRequest(name: String, role: String)
