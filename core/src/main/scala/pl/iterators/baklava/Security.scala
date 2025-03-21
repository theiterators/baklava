package pl.iterators.baklava

import java.net.URI

sealed trait Security {
  val `type`: String
  val description: String
  def descriptionParsed: Option[String] = if (description.trim.isEmpty) None else Some(description.trim)
}

case class SecurityScheme(name: String, security: Security)

case class AppliedSecurity(security: Security, params: Map[String, String])

private[baklava] case object NoopSecurity extends Security with Serializable {
  override val `type`: String      = "noop"
  override val description: String = "this should never be rendered"
}

case class HttpBearer(bearerFormat: String = "", description: String = "") extends Security with Serializable {
  override val `type`: String = "http"
  val `scheme`: String        = "bearer"

  def apply(token: String): AppliedSecurity = AppliedSecurity(this, Map("token" -> token))
}

case class HttpBasic(description: String = "") extends Security with Serializable {
  override val `type`: String = "http"
  val `scheme`: String        = "basic"

  def apply(id: String, secret: String): AppliedSecurity = AppliedSecurity(this, Map("id" -> id, "secret" -> secret))
}

// TODO: support other schemes?

case class ApiKeyInHeader(name: String, description: String = "") extends Security with Serializable {
  override val `type`: String = "apiKey"

  def apply(apiKey: String): AppliedSecurity = AppliedSecurity(this, Map("apiKey" -> apiKey))
}

case class ApiKeyInQuery(name: String, description: String = "") extends Security with Serializable {
  override val `type`: String = "apiKey"

  def apply(apiKey: String): AppliedSecurity = AppliedSecurity(this, Map("apiKey" -> apiKey))
}

case class ApiKeyInCookie(name: String, description: String = "") extends Security with Serializable {
  override val `type`: String = "apiKey"

  def apply(apiKey: String): AppliedSecurity = AppliedSecurity(this, Map("apiKey" -> apiKey))
}

case class MutualTls(description: String = "") extends Security with Serializable {
  override val `type`: String = "mutualTLS"

  def apply(): AppliedSecurity = AppliedSecurity(this, Map.empty)
}

case class OpenIdConnectInBearer(openIdConnectUrl: URI, description: String = "") extends Security with Serializable {
  override val `type`: String = "openIdConnect"

  def apply(token: String): AppliedSecurity = AppliedSecurity(this, Map("token" -> token))
}

case class OpenIdConnectInCookie(openIdConnectUrl: URI, description: String = "") extends Security with Serializable {
  override val `type`: String = "openIdConnect"

  def apply(name: String, token: String): AppliedSecurity = AppliedSecurity(this, Map("name" -> name, "token" -> token))
}

case class OAuth2InBearer(flows: OAuthFlows, description: String = "") extends Security with Serializable {
  override val `type`: String = "oauth2"

  def apply(token: String): AppliedSecurity = AppliedSecurity(this, Map("token" -> token))
}

case class OAuth2InCookie(flows: OAuthFlows, description: String = "") extends Security with Serializable {
  override val `type`: String = "oauth2"

  def apply(name: String, token: String): AppliedSecurity = AppliedSecurity(this, Map("name" -> name, "token" -> token))
}

// TODO: OpenIdConnect, OAuth2 can provide token in query, customer header, POST-form, etc.

case class OAuthFlows(
    implicitFlow: Option[OAuthImplicitFlow] = None,
    passwordFlow: Option[OAuthPasswordFlow] = None,
    clientCredentialsFlow: Option[OAuthClientCredentialsFlow] = None,
    authorizationCodeFlow: Option[OAuthAuthorizationCodeFlow] = None
)

case class OAuthImplicitFlow(
    authorizationUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

case class OAuthPasswordFlow(
    tokenUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

case class OAuthClientCredentialsFlow(
    tokenUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)

case class OAuthAuthorizationCodeFlow(
    authorizationUrl: URI,
    tokenUrl: URI,
    refreshUrl: Option[URI] = None,
    scopes: Map[String, String] = Map.empty
)
