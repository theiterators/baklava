package pl.iterators.baklava

case class Multipart(parts: Part*)

object Multipart {
  implicit val schema: Schema[Multipart] = FreeFormSchema[Multipart]("Multipart")
}

sealed trait Part {
  def name: String
}

/** Leave `filename` empty to omit it from the part's `Content-Disposition` header. */
case class FilePart(name: String, contentType: String, filename: String, bytes: Array[Byte]) extends Part

object FilePart {
  def apply(name: String, contentType: String, bytes: Array[Byte]): FilePart =
    FilePart(name, contentType, "", bytes)
}

case class TextPart(name: String, value: String) extends Part
