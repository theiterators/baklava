package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait BaklavaOpenApiPostProcessor {
  def process(openAPI: OpenAPI): OpenAPI
}

object BaklavaOpenApiPostProcessor {
  lazy val postProcessors: Seq[BaklavaOpenApiPostProcessor] = {
    new Reflections(
      new ConfigurationBuilder()
        .forPackages("") // Empty prefix scans the entire classpath
        .addScanners(Scanners.SubTypes)
    )
      .getSubTypesOf(classOf[BaklavaOpenApiPostProcessor])
      .asScala
      .map { specClazz =>
        specClazz.getConstructor().newInstance()
      }
      .toSeq
  }

}
