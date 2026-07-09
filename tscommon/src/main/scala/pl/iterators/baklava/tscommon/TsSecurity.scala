package pl.iterators.baklava.tscommon

import pl.iterators.baklava.*

/** Renders captured security schemes for the TypeScript formats: the per-route OpenAPI `security` requirement (referencing schemes by
  * name), the document-level `securitySchemes` definitions object, and a human-readable note for documentary formats.
  */
object TsSecurity {

  private def esc(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

  private def q(s: String): String = "\"" + esc(s) + "\""

  private val jsIdentifier                = "[A-Za-z_$][A-Za-z0-9_$]*".r
  private def tsKey(name: String): String = if (jsIdentifier.matches(name)) name else q(name)

  /** Distinct scheme names required by a route, sorted. */
  def schemeNames(schemes: Seq[BaklavaSecuritySchemaSerializable]): Seq[String] =
    schemes.map(_.name).distinct.sorted

  /** The OpenAPI per-route `security` array, e.g. `[{ basicAuth: [] }]`. Each entry is one accepted scheme (OpenAPI semantics: the array
    * is a logical OR of alternatives). None when the route captured no schemes.
    */
  def securityRequirement(schemes: Seq[BaklavaSecuritySchemaSerializable]): Option[String] = {
    val names = schemeNames(schemes)
    if (names.isEmpty) None
    else Some("[" + names.map(n => s"{ ${tsKey(n)}: [] }").mkString(", ") + "]")
  }

  /** A one-line human-readable requirement for documentary formats. */
  def note(schemes: Seq[BaklavaSecuritySchemaSerializable]): Option[String] = {
    val parts = schemes.map(s => s"${s.name} (${humanType(s.security)})").distinct.sorted
    parts match {
      case Nil        => None
      case one :: Nil => Some(s"Requires authentication: $one.")
      case many       => Some(s"Requires authentication (one of): ${many.mkString(", ")}.")
    }
  }

  /** The document-level `securitySchemes` object literal (an OpenAPI Security Scheme Object per name), deduped by name. None when there
    * are no schemes anywhere.
    */
  def securitySchemesObject(allSchemes: Seq[BaklavaSecuritySchemaSerializable]): Option[String] = {
    val byName = allSchemes.groupBy(_.name).toSeq.sortBy(_._1).map { case (name, list) => name -> list.head.security }
    if (byName.isEmpty) None
    else Some("{\n" + byName.map { case (name, sec) => s"  ${tsKey(name)}: ${schemeObject(sec)}" }.mkString(",\n") + "\n}")
  }

  private def humanType(sec: BaklavaSecuritySerializable): String =
    sec.httpBasic
      .map(_ => "HTTP Basic")
      .orElse(sec.httpBearer.map(b => if (b.bearerFormat.trim.nonEmpty) s"HTTP Bearer, ${b.bearerFormat.trim}" else "HTTP Bearer"))
      .orElse(sec.apiKeyInHeader.map(k => s"API key in header ${k.name}"))
      .orElse(sec.apiKeyInQuery.map(k => s"API key in query ${k.name}"))
      .orElse(sec.apiKeyInCookie.map(k => s"API key in cookie ${k.name}"))
      .orElse(sec.oAuth2InBearer.map(_ => "OAuth2"))
      .orElse(sec.oAuth2InCookie.map(_ => "OAuth2"))
      .orElse(sec.openIdConnectInBearer.map(_ => "OpenID Connect"))
      .orElse(sec.openIdConnectInCookie.map(_ => "OpenID Connect"))
      .getOrElse("authentication")

  private def obj(fields: Seq[(String, String)]): String =
    "{ " + fields.map { case (k, v) => s"$k: $v" }.mkString(", ") + " }"

  private def descField(d: String): Seq[(String, String)] =
    if (d.trim.nonEmpty) Seq("description" -> q(d.trim)) else Nil

  private def schemeObject(sec: BaklavaSecuritySerializable): String =
    sec.httpBasic
      .map(b => obj(Seq("type" -> q("http"), "scheme" -> q("basic")) ++ descField(b.description)))
      .orElse(
        sec.httpBearer.map(b =>
          obj(
            Seq("type" -> q("http"), "scheme" -> q("bearer")) ++
              (if (b.bearerFormat.trim.nonEmpty) Seq("bearerFormat" -> q(b.bearerFormat.trim)) else Nil) ++
              descField(b.description)
          )
        )
      )
      .orElse(
        sec.apiKeyInHeader.map(k => obj(Seq("type" -> q("apiKey"), "in" -> q("header"), "name" -> q(k.name)) ++ descField(k.description)))
      )
      .orElse(
        sec.apiKeyInQuery.map(k => obj(Seq("type" -> q("apiKey"), "in" -> q("query"), "name" -> q(k.name)) ++ descField(k.description)))
      )
      .orElse(
        sec.apiKeyInCookie.map(k => obj(Seq("type" -> q("apiKey"), "in" -> q("cookie"), "name" -> q(k.name)) ++ descField(k.description)))
      )
      .orElse(sec.oAuth2InBearer.map(o => obj(Seq("type" -> q("oauth2"), "flows" -> flowsObject(o.flows)) ++ descField(o.description))))
      .orElse(sec.oAuth2InCookie.map(o => obj(Seq("type" -> q("oauth2"), "flows" -> flowsObject(o.flows)) ++ descField(o.description))))
      .orElse(
        sec.openIdConnectInBearer.map(o =>
          obj(Seq("type" -> q("openIdConnect"), "openIdConnectUrl" -> q(o.openIdConnectUrl)) ++ descField(o.description))
        )
      )
      .orElse(
        sec.openIdConnectInCookie
          .map(o => obj(Seq("type" -> q("openIdConnect"), "openIdConnectUrl" -> q(o.openIdConnectUrl)) ++ descField(o.description)))
      )
      .getOrElse(obj(Seq("type" -> q("http"), "scheme" -> q("basic"))))

  private def scopesField(scopes: Map[String, String]): Seq[(String, String)] = {
    val body = scopes.toSeq.sortBy(_._1).map { case (k, v) => s"${q(k)}: ${q(v)}" }.mkString(", ")
    Seq("scopes" -> ("{ " + body + " }"))
  }

  private def flowsObject(flows: OAuthFlows): String = {
    val entries = Seq(
      flows.implicitFlow.map(f =>
        "implicit" -> obj(
          Seq("authorizationUrl" -> q(f.authorizationUrl)) ++ f.refreshUrl.map(u => "refreshUrl" -> q(u)).toSeq ++ scopesField(f.scopes)
        )
      ),
      flows.passwordFlow.map(f =>
        "password" -> obj(Seq("tokenUrl" -> q(f.tokenUrl)) ++ f.refreshUrl.map(u => "refreshUrl" -> q(u)).toSeq ++ scopesField(f.scopes))
      ),
      flows.clientCredentialsFlow.map(f =>
        "clientCredentials" -> obj(
          Seq("tokenUrl" -> q(f.tokenUrl)) ++ f.refreshUrl.map(u => "refreshUrl" -> q(u)).toSeq ++ scopesField(f.scopes)
        )
      ),
      flows.authorizationCodeFlow.map(f =>
        "authorizationCode" -> obj(
          Seq("authorizationUrl" -> q(f.authorizationUrl), "tokenUrl" -> q(f.tokenUrl)) ++ f.refreshUrl
            .map(u => "refreshUrl" -> q(u))
            .toSeq ++ scopesField(f.scopes)
        )
      )
    ).flatten
    obj(entries)
  }
}
