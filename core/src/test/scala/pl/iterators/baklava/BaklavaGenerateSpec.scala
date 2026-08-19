package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.StatusCode

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

class BaklavaGenerateSpec extends AnyFunSpec with Matchers {

  private class RecordingFormatter extends BaklavaDslFormatter {
    var invocations: List[Seq[BaklavaSerializableCall]]                                         = Nil
    override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall]): Unit =
      invocations = invocations :+ calls
  }

  private val call = BaklavaSerializableCall(
    BaklavaRequestContextSerializable(
      symbolicPath = "/pets/{id}",
      path = "/pets/1",
      pathDescription = None,
      pathSummary = None,
      method = None,
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
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
      formatter.invocations shouldBe List(Seq(call))
    }
  }
}
