package pl.iterators.baklava.sttp4

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.{FilePart, Multipart, TextPart}

import java.nio.charset.StandardCharsets.UTF_8

class SttpBodiesSpec extends AnyFunSpec with Matchers {

  describe("urlEncodeForm") {
    it("url-encodes keys and values and joins pairs with &") {
      SttpBodies.urlEncodeForm(Seq("a b" -> "c&d", "ż" -> "x")) shouldBe "a+b=c%26d&%C5%BC=x"
    }

    it("renders an empty field list as an empty string") {
      SttpBodies.urlEncodeForm(Seq.empty) shouldBe ""
    }
  }

  describe("renderMultipart") {
    it("renders text parts with the fixed boundary") {
      val rendered = new String(SttpBodies.renderMultipart(Multipart(TextPart("greeting", "hello"))), UTF_8)
      rendered shouldBe
      "--baklava-multipart-boundary\r\n" +
      "Content-Disposition: form-data; name=\"greeting\"\r\n\r\n" +
      "hello\r\n" +
      "--baklava-multipart-boundary--\r\n"
    }

    it("renders file parts with filename and content type") {
      val rendered = new String(
        SttpBodies.renderMultipart(Multipart(FilePart("logo", "image/png", "logo.png", "PNG".getBytes(UTF_8)))),
        UTF_8
      )
      rendered shouldBe
      "--baklava-multipart-boundary\r\n" +
      "Content-Disposition: form-data; name=\"logo\"; filename=\"logo.png\"\r\n" +
      "Content-Type: image/png\r\n\r\n" +
      "PNG\r\n" +
      "--baklava-multipart-boundary--\r\n"
    }

    it("omits filename from Content-Disposition when it is empty") {
      val rendered = new String(
        SttpBodies.renderMultipart(Multipart(FilePart("blob", "application/octet-stream", "BYTES".getBytes(UTF_8)))),
        UTF_8
      )
      rendered should include("Content-Disposition: form-data; name=\"blob\"\r\n")
      (rendered should not).include("filename")
    }

    it("advertises the fixed boundary in the content type") {
      SttpBodies.multipartContentType shouldBe "multipart/form-data; boundary=baklava-multipart-boundary"
    }

    it("escapes quotes and CRLF in part names") {
      val rendered = new String(SttpBodies.renderMultipart(Multipart(TextPart("a\"b\r\nc", "v"))), UTF_8)
      rendered should include("""Content-Disposition: form-data; name="a%22b%0D%0Ac"""")
    }

    it("escapes quotes and CRLF in filenames") {
      val rendered = new String(
        SttpBodies.renderMultipart(Multipart(FilePart("f", "text/plain", "evil\"file\r\n.txt", "X".getBytes(UTF_8)))),
        UTF_8
      )
      rendered should include("""filename="evil%22file%0D%0A.txt"""")
    }
  }

  describe("SttpBodyContent") {
    it("compares bytes by value, not reference") {
      val a = SttpBodyContent("payload".getBytes(UTF_8), "text/plain")
      val b = SttpBodyContent("payload".getBytes(UTF_8), "text/plain")
      a shouldBe b
      a.hashCode shouldBe b.hashCode
    }

    it("distinguishes different bytes and different content types") {
      val base = SttpBodyContent("payload".getBytes(UTF_8), "text/plain")
      SttpBodyContent("other".getBytes(UTF_8), "text/plain") should not be base
      SttpBodyContent("payload".getBytes(UTF_8), "application/json") should not be base
    }
  }
}
