package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Header => SttpHeader, Method, StatusCode}

import scala.jdk.CollectionConverters.*

class OptionQueryParameterDefaultSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI generation for Option[T] query parameters (regression for #49)") {

    it("does not emit a default field when none is explicitly set") {
      val optionSchema = BaklavaSchemaSerializable(Schema.optionSchema(Schema.stringSchema))
      val call         = syntheticCall(BaklavaQueryParamSerializable("filter", None, optionSchema))

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val parameters  = openAPI.getPaths.get("/items").getGet.getParameters.asScala
      val filterParam = parameters.find(_.getName == "filter").getOrElse(fail("filter parameter not found"))
      filterParam.getSchema.getDefault shouldBe null
    }
  }

  private def syntheticCall(queryParam: BaklavaQueryParamSerializable): BaklavaSerializableCall =
    BaklavaSerializableCall(
      request = BaklavaRequestContextSerializable(
        symbolicPath = "/items",
        path = "/items",
        pathDescription = None,
        pathSummary = None,
        method = Some(Method("GET")),
        operationDescription = None,
        operationSummary = None,
        operationId = None,
        operationTags = Seq.empty,
        securitySchemes = Seq.empty,
        bodySchema = None,
        headersSeq = Seq.empty,
        pathParametersSeq = Seq.empty,
        queryParametersSeq = Seq(queryParam),
        responseDescription = None,
        responseHeaders = Seq.empty
      ),
      response = BaklavaResponseContextSerializable(
        protocol = BaklavaHttpProtocol("HTTP/1.1"),
        status = StatusCode(200),
        headers = Seq.empty,
        requestBodyString = "",
        responseBodyString = "",
        requestContentType = None,
        responseContentType = None,
        bodySchema = None
      )
    )
}
