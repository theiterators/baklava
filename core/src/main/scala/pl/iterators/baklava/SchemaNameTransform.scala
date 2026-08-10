package pl.iterators.baklava

object SchemaNameTransform {
  // acronym-aware: "myHTTPStatus" → "my" | "HTTP" | "Status"
  private def delimited(name: String, sep: String): String =
    name
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1" + sep + "$2")
      .replaceAll("([a-z\\d])([A-Z])", "$1" + sep + "$2")

  def snakeCase(name: String): String = delimited(name, "_").toLowerCase

  def screamingSnakeCase(name: String): String = delimited(name, "_").toUpperCase

  def kebabCase(name: String): String = delimited(name, "-").toLowerCase
}
