package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import sttp.model.{Method, StatusCode}

// a spec mixing SchemaDefaults (e.g. the naming-strategy pattern) resolves its own
// emptyBodySchema instance, so identity comparisons against Schema.emptyBodySchema miss
class EmptyBodySchemaIdentitySpec extends AnyFunSpec with Matchers {

  private val mixinEmptyBodySchema = new SchemaDefaults {}.emptyBodySchema

  it("recognizes any SchemaDefaults instance's EmptyBody schema") {
    Schema.isEmptyBody(Schema.emptyBodySchema) shouldBe true
    Schema.isEmptyBody(mixinEmptyBodySchema) shouldBe true
    Schema.isEmptyBody(Schema.stringSchema) shouldBe false
  }

  it("drops a mixin-resolved EmptyBody schema from serialized requests") {
    BaklavaRequestContextSerializable(requestContext(Some(mixinEmptyBodySchema)), "").bodySchema shouldBe None
  }

  it("drops a mixin-resolved EmptyBody schema from serialized responses") {
    val response = BaklavaResponseContext[EmptyBody, Unit, Unit](
      protocol = BaklavaHttpProtocol("HTTP/1.1"),
      status = StatusCode.Ok,
      headers = Seq.empty,
      body = EmptyBodyInstance,
      rawRequest = (),
      requestBodyString = "",
      rawResponse = (),
      responseBodyString = "",
      requestContentType = None,
      responseContentType = None,
      bodySchema = Some(mixinEmptyBodySchema)
    )
    BaklavaResponseContextSerializable(response).bodySchema shouldBe None
  }

  private def requestContext(
      bodySchema: Option[Schema[EmptyBody]]
  ): BaklavaRequestContext[EmptyBody, Unit, Unit, Unit, Unit, Unit, Unit] =
    BaklavaRequestContext[EmptyBody, Unit, Unit, Unit, Unit, Unit, Unit](
      symbolicPath = "/x",
      path = "/x",
      pathDescription = None,
      pathSummary = None,
      method = Some(Method.GET),
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = Seq.empty,
      securitySchemes = Seq.empty,
      body = None,
      bodySchema = bodySchema,
      headers = Seq.empty,
      headersDefinition = (),
      headersProvided = (),
      headersSeq = Seq.empty,
      security = AppliedSecurity(NoopSecurity, Map.empty),
      pathParameters = (),
      pathParametersProvided = (),
      pathParametersSeq = Seq.empty,
      queryParameters = (),
      queryParametersProvided = (),
      queryParametersSeq = Seq.empty,
      responseDescription = None,
      responseHeaders = Seq.empty
    )
}
