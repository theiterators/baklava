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
  }
}
