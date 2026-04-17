package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class BaklavaOpenApiPostProcessorSpec extends AnyFunSpec with Matchers {

  describe("BaklavaOpenApiPostProcessor.postProcessorsFor") {

    it("skips abstract subtypes without crashing") {
      // BaklavaOpenApiPostProcessorSpec$NoOpPostProcessor is concrete; the abstract helper
      // BaklavaOpenApiPostProcessorSpec$AbstractPostProcessor must be filtered out by Modifier.isAbstract.
      val processors = BaklavaOpenApiPostProcessor.postProcessorsFor(
        Map(BaklavaOpenApiPostProcessor.PostProcessorPackagesKey -> "pl.iterators.baklava.openapi")
      )
      processors.exists(_.isInstanceOf[NoOpPostProcessor]) shouldBe true
      processors.exists(_.isInstanceOf[AbstractPostProcessor]) shouldBe false
    }

    it("skips classes with a constructor that throws, logging the failure instead of crashing") {
      val processors = BaklavaOpenApiPostProcessor.postProcessorsFor(
        Map(BaklavaOpenApiPostProcessor.PostProcessorPackagesKey -> "pl.iterators.baklava.openapi")
      )
      // ThrowingPostProcessor throws in its constructor; it must be skipped (not crash the whole call).
      processors.exists(_.isInstanceOf[ThrowingPostProcessor]) shouldBe false
    }

    it("honors the scan-package config key") {
      // When scoped to a package that contains no subtypes, we should get an empty seq.
      val processors = BaklavaOpenApiPostProcessor.postProcessorsFor(
        Map(BaklavaOpenApiPostProcessor.PostProcessorPackagesKey -> "this.package.does.not.exist")
      )
      processors shouldBe empty
    }
  }
}

// Concrete: should be discovered and instantiated.
class NoOpPostProcessor extends BaklavaOpenApiPostProcessor {
  override def process(openAPI: OpenAPI): Unit = ()
}

// Abstract: should be filtered out by Modifier.isAbstract.
abstract class AbstractPostProcessor extends BaklavaOpenApiPostProcessor

// Throws in constructor: should be caught, logged, and skipped.
// The throw is conditional on a runtime check so scalac doesn't flag dead code.
class ThrowingPostProcessor extends BaklavaOpenApiPostProcessor {
  if (java.lang.System.currentTimeMillis() > 0L) throw new RuntimeException("intentional failure for test")
  override def process(openAPI: OpenAPI): Unit = ()
}
