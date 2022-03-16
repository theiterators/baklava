package pl.iterators.baklava.core.model

sealed trait RouteSecurity {
  val schemaName: String
}

object RouteSecurity {
  case class Bearer(schemaName: String = "bearerAuth") extends RouteSecurity {
    override def toString: String = "Bearer"
  }

  case class Basic(schemaName: String = "basicAuth") extends RouteSecurity {
    override def toString: String = "Basic"
  }

  case class HeaderApiKey(name: String, schemaName: String = "headerApiKeyAuth") extends RouteSecurity {
    override def toString: String = s"ApiKey (Header: $name)"
  }

  case class QueryApiKey(name: String, schemaName: String = "queryApiKeyAuth") extends RouteSecurity {
    override def toString: String = s"ApiKey (Query: $name)"
  }

  case class CookieApiKey(name: String, schemaName: String = "cookieApiKeyAuth") extends RouteSecurity {
    override def toString: String = s"ApiKey (Cookie: $name)"
  }
}
