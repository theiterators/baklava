package pl.iterators.baklava.openapi

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, PrintStream}

class BaklavaDslFormatterOpenAPIConfigSpec extends AnyFunSpec with Matchers {

  describe("BaklavaDslFormatterOpenAPI.create") {

    it("logs a diagnostic to stderr when openapi-info is malformed instead of silently dropping it") {
      val buffer   = new ByteArrayOutputStream()
      val err      = System.err
      val captured =
        try {
          System.setErr(new PrintStream(buffer))
          val formatter = new BaklavaDslFormatterOpenAPI
          formatter.create(
            Map(
              "openapi-info"                                       -> "this is definitely not valid openapi yaml",
              BaklavaOpenApiPostProcessor.PostProcessorPackagesKey -> "pl.no.such.package"
            ),
            calls = Nil
          )
          buffer.toString
        } finally System.setErr(err)

      // Either a swagger-parser warning line OR our fallback diagnostic — both count as "user was told".
      val userNotified =
        captured.contains("Baklava: openapi-info parse message") ||
          captured.contains("Baklava: unable to parse openapi-info")
      userNotified shouldBe true
    }
  }
}
