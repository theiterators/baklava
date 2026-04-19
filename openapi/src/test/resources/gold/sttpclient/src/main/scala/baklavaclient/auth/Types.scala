package baklavaclient.auth

import baklavaclient.common.User

final case class LoginForm(client_id: String, grant_type: String)

final case class LoginResponse(token: String, user: User)
