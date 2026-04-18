package pl.iterators.baklava

/** `multipart/form-data` request body (issue #81).
  *
  * Holds a sequence of named parts — each either a `FilePart` (binary data with its own content-type and optional filename) or a
  * `TextPart` (plain-string form field). The adapter marshaller (pekko-http or http4s) turns this into a boundary-delimited wire body with
  * `Content-Type: multipart/form-data; boundary=…`.
  *
  * The OpenAPI render for this body is a free-form object (`type: object`) — describing the exact per-part schema would require deriving
  * one at runtime from the parts. Users who need richer docs can replace the implicit `Schema[Multipart]` with a hand-crafted one.
  */
case class Multipart(parts: Part*)

object Multipart {
  implicit val schema: Schema[Multipart] = FreeFormSchema[Multipart]("Multipart")
}

/** One part of a multipart body. Sealed so marshallers can pattern-match exhaustively. */
sealed trait Part {

  /** Form-field name, e.g. `"avatar"` or `"caption"`. */
  def name: String
}

/** A binary upload part. The `contentType` becomes the `Content-Type` header of this specific part (inside the multipart body),
  * independent of the outer request content-type. `filename` is rendered into the part's
  * `Content-Disposition: form-data; name="…"; filename="…"` header — it is what a server would see as the uploaded filename. Leave it
  * empty to omit the filename.
  */
case class FilePart(name: String, contentType: String, filename: String, bytes: Array[Byte]) extends Part

object FilePart {

  /** Convenience for the common case where you don't care about the filename. */
  def apply(name: String, contentType: String, bytes: Array[Byte]): FilePart =
    FilePart(name, contentType, "", bytes)
}

/** A text form field. Equivalent to a regular URL-encoded form field, just riding on the multipart transport so it can coexist with
  * `FilePart`s in the same request.
  */
case class TextPart(name: String, value: String) extends Part
