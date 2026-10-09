package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

class BaklavaGenerateSpec extends AnyFunSpec with Matchers {

  private class RecordingFormatter extends BaklavaDslFormatter {
    var invocations: List[(String, Seq[BaklavaSerializableCall])]                                                   = Nil
    override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall], outputRoot: String): Unit =
      invocations = invocations :+ (outputRoot -> calls)
  }

  private val call      = callAt("/pets/{id}", "/pets/1", tags = Seq("public"))
  private val adminCall = callAt("/admin/loggers/{name}", "/admin/loggers/root", tags = Seq("Admin"))

  private def callAt(symbolicPath: String, path: String, tags: Seq[String]) = BaklavaSerializableCall(
    BaklavaRequestContextSerializable(
      symbolicPath = symbolicPath,
      path = path,
      pathDescription = None,
      pathSummary = None,
      method = None,
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = tags,
      securitySchemes = Seq.empty,
      bodySchema = None,
      headersSeq = Seq.empty,
      pathParametersSeq = Seq.empty,
      queryParametersSeq = Seq.empty,
      responseDescription = None,
      responseHeaders = Seq.empty
    ),
    BaklavaResponseContextSerializable(
      protocol = BaklavaHttpProtocol("HTTP/1.1"),
      status = StatusCode.Ok,
      headers = Seq.empty,
      requestContentType = None,
      responseContentType = None,
      bodySchema = None
    )
  )

  private def captureStdErr[T](body: => T): (T, String) = {
    val buffer   = new ByteArrayOutputStream()
    val original = System.err
    System.setErr(new PrintStream(buffer, true, "UTF-8"))
    try {
      val result = body
      (result, new String(buffer.toByteArray, StandardCharsets.UTF_8))
    } finally System.setErr(original)
  }

  describe("BaklavaGenerate.generate") {

    it("skips formatters when zero calls were captured, so existing output is not overwritten") {
      val formatter     = new RecordingFormatter
      val (ran, stderr) = captureStdErr(BaklavaGenerate.generate(Map.empty, Seq.empty, Seq(formatter)))

      ran shouldBe false
      formatter.invocations shouldBe empty
      stderr should include("0")
      stderr should include("testFull")
    }

    it("runs formatters when calls were captured") {
      val formatter = new RecordingFormatter
      val ran       = BaklavaGenerate.generate(Map("k" -> "v"), Seq(call), Seq(formatter))

      ran shouldBe true
      formatter.invocations shouldBe List("target/baklava" -> Seq(call))
    }

    it("runs every formatter again per view, with that view's calls under target/baklava/views/<name>") {
      val formatter = new RecordingFormatter
      val config    = Map(
        "views" -> """[
          { "name": "client", "filter": "path-excludes:^/admin/" },
          { "name": "public", "filter": ["tag-includes:public", "path-includes:^/pets"] },
          { "name": "everything" }
        ]"""
      )
      val ran = BaklavaGenerate.generate(config, Seq(call, adminCall), Seq(formatter))

      ran shouldBe true
      formatter.invocations shouldBe List(
        "target/baklava"                  -> Seq(call, adminCall),
        "target/baklava/views/client"     -> Seq(call),
        "target/baklava/views/public"     -> Seq(call),
        "target/baklava/views/everything" -> Seq(call, adminCall)
      )
    }

    it("skips a view that selects no calls, keeping the full output") {
      val formatter     = new RecordingFormatter
      val config        = Map("views" -> """[{ "name": "none", "filter": "tag-includes:nope" }]""")
      val (ran, stderr) = captureStdErr(BaklavaGenerate.generate(config, Seq(call), Seq(formatter)))

      ran shouldBe true
      formatter.invocations shouldBe List("target/baklava" -> Seq(call))
      stderr should include("view 'none' selected 0 of 1")
    }

    it("fails before running any formatter when the views configuration is invalid") {
      val formatter = new RecordingFormatter
      val config    = Map("views" -> """[{ "name": "client", "filter": "by-moon-phase:full" }]""")

      val error = intercept[RuntimeException](BaklavaGenerate.generate(config, Seq(call), Seq(formatter)))
      error.getMessage should include("by-moon-phase:full")
      formatter.invocations shouldBe empty
    }
  }
}
