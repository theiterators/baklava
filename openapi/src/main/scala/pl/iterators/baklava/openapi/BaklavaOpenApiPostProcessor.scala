package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

import java.lang.reflect.Modifier
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.{Failure, Success, Try}

trait BaklavaOpenApiPostProcessor {
  def process(openAPI: OpenAPI): Unit
}

object BaklavaOpenApiPostProcessor {

  /** Config key for narrowing the Reflections classpath scan. Comma-separated list of package prefixes, e.g. "com.example,org.acme".
    * Default is empty, which scans the entire classpath (back-compat). Projects with large classpaths should set this to their own root
    * package to avoid slow startup and JDK 17+ illegal-access warnings.
    */
  val PostProcessorPackagesKey = "baklava.postProcessorPackages"

  /** Back-compat entry point: scans the entire classpath. Prefer `postProcessorsFor(config)`. */
  def postProcessors: Seq[BaklavaOpenApiPostProcessor] = postProcessorsFor(Map.empty)

  def postProcessorsFor(config: Map[String, String]): Seq[BaklavaOpenApiPostProcessor] = {
    val packages = config
      .get(PostProcessorPackagesKey)
      .toSeq
      .flatMap(_.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)

    val configBuilder = new ConfigurationBuilder().addScanners(Scanners.SubTypes)
    if (packages.isEmpty) configBuilder.forPackages("") else packages.foreach(p => configBuilder.forPackages(p))

    new Reflections(configBuilder)
      .getSubTypesOf(classOf[BaklavaOpenApiPostProcessor])
      .asScala
      .toSeq
      .filterNot(clazz => Modifier.isAbstract(clazz.getModifiers) || clazz.isInterface)
      .flatMap { clazz =>
        Try(clazz.getDeclaredConstructor().newInstance()) match {
          case Success(instance) => Some(instance)
          case Failure(e)        =>
            System.err.println(
              s"Baklava: failed to instantiate post-processor ${clazz.getName} — skipping. Cause: ${e.getClass.getSimpleName}: ${e.getMessage}"
            )
            None
        }
      }
  }

}
