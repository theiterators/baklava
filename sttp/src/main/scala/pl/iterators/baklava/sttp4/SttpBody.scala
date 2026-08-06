package pl.iterators.baklava.sttp4

import pl.iterators.baklava.{FilePart, Multipart, TextPart}

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8

// bytes + the Content-Type they were rendered with; bodies are rendered eagerly so
// assertions and the serializer can both read them
final case class SttpBodyContent(bytes: Array[Byte], contentType: String) {
  // Array[Byte] would otherwise degrade the case-class equals/hashCode to reference semantics
  override def equals(other: Any): Boolean = other match {
    case that: SttpBodyContent => java.util.Arrays.equals(bytes, that.bytes) && contentType == that.contentType
    case _                     => false
  }
  override def hashCode(): Int = 31 * java.util.Arrays.hashCode(bytes) + contentType.hashCode
}

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

  // quotes and CR/LF would corrupt the Content-Disposition line; percent-encode them like browsers do (WHATWG)
  private def escapeDispositionValue(value: String): String =
    value.replace("\"", "%22").replace("\r", "%0D").replace("\n", "%0A")

  def renderMultipart(multipart: Multipart): Array[Byte] = {
    val out                    = new ByteArrayOutputStream()
    def write(s: String): Unit = out.write(s.getBytes(UTF_8))
    multipart.parts.foreach {
      case FilePart(name, contentType, filename, bytes) =>
        write(s"--$multipartBoundary\r\n")
        val filenameSuffix = if (filename.isEmpty) "" else s"""; filename="${escapeDispositionValue(filename)}""""
        write(s"""Content-Disposition: form-data; name="${escapeDispositionValue(name)}"$filenameSuffix\r\n""")
        write(s"Content-Type: $contentType\r\n\r\n")
        out.write(bytes)
        write("\r\n")
      case TextPart(name, value) =>
        write(s"--$multipartBoundary\r\n")
        write(s"""Content-Disposition: form-data; name="${escapeDispositionValue(name)}"\r\n\r\n""")
        write(value)
        write("\r\n")
    }
    write(s"--$multipartBoundary--\r\n")
    out.toByteArray
  }
}
