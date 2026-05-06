package pl.iterators.baklava.routes

import scala.util.Try

final case class BaklavaRoutesConfig(
    enabled: Boolean = true,
    basicAuthUser: Option[String] = None,
    basicAuthPassword: Option[String] = None,
    fileSystemPath: String = "./target/baklava",
    publicPathPrefix: String = "/",
    apiPublicPathPrefix: String = "/v1"
)

object BaklavaRoutesConfig {
  def fromEnv: BaklavaRoutesConfig = fromEnv(sys.env)

  def fromEnv(env: Map[String, String]): BaklavaRoutesConfig = {
    val default = BaklavaRoutesConfig()
    BaklavaRoutesConfig(
      enabled = env.get("BAKLAVA_ROUTES_ENABLED").map(_.toBoolean).getOrElse(default.enabled),
      basicAuthUser = env.get("BAKLAVA_ROUTES_BASIC_AUTH_USER").orElse(default.basicAuthUser),
      basicAuthPassword = env.get("BAKLAVA_ROUTES_BASIC_AUTH_PASSWORD").orElse(default.basicAuthPassword),
      fileSystemPath = env.getOrElse("BAKLAVA_ROUTES_FILESYSTEM_PATH", default.fileSystemPath),
      publicPathPrefix = env.getOrElse("BAKLAVA_ROUTES_PUBLIC_PATH_PREFIX", default.publicPathPrefix),
      apiPublicPathPrefix = env.getOrElse("BAKLAVA_ROUTES_API_PUBLIC_PATH_PREFIX", default.apiPublicPathPrefix)
    )
  }

  def fromTypesafeConfig(config: com.typesafe.config.Config): BaklavaRoutesConfig = {
    val c = config.getConfig("baklava-routes")
    BaklavaRoutesConfig(
      enabled = c.getBoolean("enabled"),
      basicAuthUser = Try(c.getString("basic-auth-user")).toOption,
      basicAuthPassword = Try(c.getString("basic-auth-password")).toOption,
      fileSystemPath = c.getString("filesystem-path"),
      publicPathPrefix = c.getString("public-path-prefix"),
      apiPublicPathPrefix = c.getString("api-public-path-prefix")
    )
  }
}
