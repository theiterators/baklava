package pl.iterators.baklava.sttp4

import pl.iterators.baklava.{FilePart, Multipart, TextPart}

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

// bytes + the Content-Type they were rendered with; bodies are rendered eagerly so
// assertions and the serializer can both read them
final case class SttpBodyContent(bytes: Array[Byte], contentType: String)

trait ToSttpBody[T] {
  // None = no body on the wire
  def apply(t: T): Option[SttpBodyContent]
}

trait FromSttpBody[T] {
  def apply(bytes: Array[Byte]): Either[Throwable, T]
}

object SttpBodies {
  // Fixed boundary keeps the captured request body byte-stable across gold-test runs.
  val multipartBoundary    = "baklava-multipart-boundary"
  val multipartContentType = s"multipart/form-data; boundary=$multipartBoundary"

  def urlEncodeForm(fields: Seq[(String, String)]): String =
    fields.map { case (k, v) => s"${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }.mkString("&")

  def renderMultipart(multipart: Multipart): Array[Byte] = {
    val out                    = new ByteArrayOutputStream()
    def write(s: String): Unit = out.write(s.getBytes(UTF_8))
    multipart.parts.foreach {
      case FilePart(name, contentType, filename, bytes) =>
        write(s"--$multipartBoundary\r\n")
        val filenameSuffix = if (filename.isEmpty) "" else s"""; filename="$filename""""
        write(s"""Content-Disposition: form-data; name="$name"$filenameSuffix\r\n""")
        write(s"Content-Type: $contentType\r\n\r\n")
        out.write(bytes)
        write("\r\n")
      case TextPart(name, value) =>
        write(s"--$multipartBoundary\r\n")
        write(s"""Content-Disposition: form-data; name="$name"\r\n\r\n""")
        write(value)
        write("\r\n")
    }
    write(s"--$multipartBoundary--\r\n")
    out.toByteArray
  }
}
