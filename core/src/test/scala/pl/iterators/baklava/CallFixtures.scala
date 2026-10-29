package pl.iterators.baklava

import sttp.model.StatusCode

/** Minimal captured calls for tests that exercise the call store and the generator; only the request path and tags vary. */
object CallFixtures {

  def callAt(symbolicPath: String, path: String, tags: Seq[String] = Nil): BaklavaSerializableCall = BaklavaSerializableCall(
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
}
