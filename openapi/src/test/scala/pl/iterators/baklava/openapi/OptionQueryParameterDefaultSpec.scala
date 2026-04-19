package pl.iterators.baklava.openapi

import io.swagger.v3.core.util.Yaml
import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import scala.jdk.CollectionConverters.*

/** Regression for issue #49. Option[T] fields used to render `default: "None"` in OpenAPI because the serializer went through
  * `value.toString`. After #61's JSON-encoding fix we encode `None` as `Json.Null`, so Option[T] fields render the semantically correct
  * `default: null` — "if the client omits the parameter, treat it as null".
  */
class OptionQueryParameterDefaultSpec extends AnyFunSpec with Matchers {

  describe("OpenAPI generation for Option[T] query parameters (regression for #49)") {

    it("emits `default: null`, not the string `None`, for an optional parameter") {
      val optionSchema = BaklavaSchemaSerializable(Schema.optionSchema(Schema.stringSchema))
      val call         = syntheticCall(BaklavaQueryParamSerializable("filter", None, optionSchema))

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))

      val filterParam = openAPI.getPaths
        .get("/items")
        .getGet
        .getParameters
        .asScala
        .find(_.getName == "filter")
        .getOrElse(fail("filter parameter not found"))

      // The swagger object holds null explicitly; `getDefault` returns a plain Java null.
      filterParam.getSchema.getDefault shouldBe null

      // And on the wire, it renders as `default: null` — crucially, *not* `default: "None"`.
      val yaml = Yaml.pretty(openAPI)
      yaml should include("default: null")
      yaml should not include ("default: \"None\"")
      yaml should not include ("default: None")
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
        bodyString = "",
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
        bodyString = "",
        requestContentType = None,
        responseContentType = None,
        bodySchema = None
      )
    )
}
