package pl.iterators.baklava.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import sttp.model.{Method, StatusCode}

import scala.jdk.CollectionConverters.*

/** Regression suite for issue #61: `Schema[T].default` used to be serialized via `.toString`, which produced Scala syntax like
  * `"List(1, 2, 3)"` or `"MyCaseClass(a, 1)"` that renders as invalid or nonsensical default values in the generated OpenAPI YAML. The
  * default is now encoded as structured `io.circe.Json` at serialization time, using `SchemaType` to pick the right primitive encoding.
  */
class SchemaDefaultJsonSpec extends AnyFunSpec with Matchers {

  describe("BaklavaSchemaSerializable.default (issue #61)") {

    it("encodes an Int default as a JSON number, not a string") {
      val schema = new Schema[Int] {
        val className: String                  = "Int"
        val `type`: SchemaType                 = SchemaType.IntegerType
        val format: Option[String]             = Some("int32")
        val properties: Map[String, Schema[?]] = Map.empty
        val items: Option[Schema[?]]           = None
        val `enum`: Option[Set[String]]        = None
        val required: Boolean                  = true
        val additionalProperties: Boolean      = false
        val default: Option[Int]               = Some(42)
        val description: Option[String]        = None
      }
      val serialized = BaklavaSchemaSerializable(schema)
      serialized.default.flatMap(_.asNumber).flatMap(_.toInt) shouldBe Some(42)
      // Critically: the JSON form is a number, not a string
      serialized.default.map(_.noSpaces) shouldBe Some("42")
    }

    it("encodes a Long default as a JSON number") {
      val schema     = primitiveSchema[Long](SchemaType.IntegerType, Some("int64"), Some(123456789012L))
      val serialized = BaklavaSchemaSerializable(schema)
      serialized.default.flatMap(_.asNumber).flatMap(_.toLong) shouldBe Some(123456789012L)
    }

    it("encodes a Boolean default as a JSON boolean") {
      val schema     = primitiveSchema[Boolean](SchemaType.BooleanType, None, Some(true))
      val serialized = BaklavaSchemaSerializable(schema)
      serialized.default.flatMap(_.asBoolean) shouldBe Some(true)
      serialized.default.map(_.noSpaces) shouldBe Some("true")
    }

    it("encodes a String default as a JSON string") {
      val schema     = primitiveSchema[String](SchemaType.StringType, None, Some("hello"))
      val serialized = BaklavaSchemaSerializable(schema)
      serialized.default.flatMap(_.asString) shouldBe Some("hello")
    }

    it("emits correct swagger default when rendered via the OpenAPI generator") {
      val schema = primitiveSchema[Int](SchemaType.IntegerType, Some("int32"), Some(42))
      val param  = BaklavaQueryParamSerializable("page", None, BaklavaSchemaSerializable(schema))
      val call   = syntheticCall(param)

      val openAPI = new OpenAPI()
      BaklavaDslFormatterOpenAPIWorker.generateForCalls(openAPI, Seq(call))
      val pageParam = openAPI.getPaths
        .get("/items")
        .getGet
        .getParameters
        .asScala
        .find(_.getName == "page")
        .getOrElse(fail("page parameter missing"))

      // `setDefault(Object)` should receive a numeric Java value so swagger's YAML writer emits
      // a plain number (e.g. `default: 42`) rather than the stringly `default: "42"` that the old
      // `.toString`-based code produced.
      val defaultValue = pageParam.getSchema.getDefault
      defaultValue shouldBe a[java.lang.Number]
      defaultValue.asInstanceOf[java.lang.Number].longValue() shouldBe 42L
      defaultValue should not be a[String]
    }
  }

  private def primitiveSchema[T](t: SchemaType, fmt: Option[String], dflt: Option[T]): Schema[T] = new Schema[T] {
    val className: String                  = t.toString
    val `type`: SchemaType                 = t
    val format: Option[String]             = fmt
    val properties: Map[String, Schema[?]] = Map.empty
    val items: Option[Schema[?]]           = None
    val `enum`: Option[Set[String]]        = None
    val required: Boolean                  = true
    val additionalProperties: Boolean      = false
    val default: Option[T]                 = dflt
    val description: Option[String]        = None
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
