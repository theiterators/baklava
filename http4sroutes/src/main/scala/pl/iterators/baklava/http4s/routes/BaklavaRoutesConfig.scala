package pl.iterators.baklava.http4s.routes

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
}
