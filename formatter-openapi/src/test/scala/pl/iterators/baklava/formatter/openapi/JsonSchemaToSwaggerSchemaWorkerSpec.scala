package pl.iterators.baklava.formatter.openapi

import com.github.andyglow.json.Value
import io.swagger.v3.oas.models.{media => swagger}
import org.specs2.mutable.Specification
import java.math.{BigDecimal => JavaBigDecimal}
import scala.collection.JavaConverters._

class JsonSchemaToSwaggerSchemaWorkerSpec extends Specification {

  val converter = new JsonSchemaToSwaggerSchemaWorker

  "should convert allof schema properly" in {
    val inner1 = json.Schema.boolean
    val inner2 = json.Schema.number[BigDecimal]

    val input1 = new json.Schema.allof(Set(inner1, inner2))
    val input2 = new json.Schema.allof(Set(inner2, inner1))
    val input3 = new json.Schema.allof(Set.empty)

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)
    val output3 = converter.convert(input3)

    output1 shouldEqual converter.convert(inner1)
    output2 shouldEqual converter.convert(inner2)
    output3 shouldEqual new swagger.Schema[Unit]()
  }

  "should convert array schema properly" in {
    val input1 = new json.Schema.array[Boolean, List](json.Schema.boolean)
    val input2 = new json.Schema.array[Double, List](json.Schema.number[Double])

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)

    output1 should haveClass[swagger.ArraySchema]
    output2 should haveClass[swagger.ArraySchema]

    output1.asInstanceOf[swagger.ArraySchema].getItems shouldEqual new swagger.BooleanSchema
    output2.asInstanceOf[swagger.ArraySchema].getItems shouldEqual new swagger.NumberSchema
  }

  "should convert array schema properly (array contains object inside)" in {
    val input = new json.Schema.array[AnyRef, List](
      json.Schema.`object`(
        json.Schema.`object`.Field("sampleString", json.Schema.string, true),
        json.Schema.`object`.Field("sampleInteger", json.Schema.integer, false),
        json.Schema.`object`.Field("sampleBoolean", json.Schema.boolean, true)
      ))

    val output = converter.convert(input)
    output should haveClass[swagger.ArraySchema]

    val outputInner = output.asInstanceOf[swagger.ArraySchema].getItems
    outputInner should haveClass[swagger.ObjectSchema]
    outputInner.asInstanceOf[swagger.ObjectSchema].getRequired.asScala should
      containTheSameElementsAs(List("sampleString", "sampleBoolean"))
    val outputInnerProperties = outputInner.getProperties.asScala
    outputInnerProperties("sampleString") should haveClass[swagger.StringSchema]
    outputInnerProperties("sampleInteger") should haveClass[swagger.IntegerSchema]
    outputInnerProperties("sampleBoolean") should haveClass[swagger.BooleanSchema]
  }

  "should convert array schema properly (array contains dict inside)" in {
    val input = json.Schema.array(new json.Schema.dictionary(json.Schema.string))

    val output = converter.convert(input)
    output should haveClass[swagger.ArraySchema]

    val outputInner = output.asInstanceOf[swagger.ArraySchema].getItems
    outputInner should haveClass[swagger.ObjectSchema]
    val outputInnerProperties = outputInner.getProperties.asScala
    outputInnerProperties("^.*$") should haveClass[swagger.StringSchema]
  }

  "should convert boolean schema properly" in {
    val input = json.Schema.boolean

    val output = converter.convert(input)

    output shouldEqual new swagger.BooleanSchema
  }

  "should convert def schema properly" in {
    val input1 = new json.Schema.`def`("sig", json.Schema.boolean)
    val input2 = new json.Schema.`def`("sig", new json.Schema.array[Float, List](json.Schema.number[Float]))

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)

    output1 shouldEqual new swagger.BooleanSchema
    output2 should haveClass[swagger.ArraySchema]
    output2.asInstanceOf[swagger.ArraySchema].getItems shouldEqual new swagger.NumberSchema
  }

  "should convert dictionary schema properly" in {
    val input1 = new json.Schema.dictionary(json.Schema.number[Double])
    val input2 = new json.Schema.dictionary(json.Schema.number[BigDecimal])
    val input3 = new json.Schema.dictionary(json.Schema.string)

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)
    val output3 = converter.convert(input3)

    output1 should haveClass[swagger.ObjectSchema]
    output2 should haveClass[swagger.ObjectSchema]
    output3 should haveClass[swagger.ObjectSchema]

    output1.asInstanceOf[swagger.ObjectSchema].getProperties.asScala shouldEqual Map("^.*$" -> new swagger.NumberSchema)
    output2.asInstanceOf[swagger.ObjectSchema].getProperties.asScala shouldEqual Map("^.*$" -> new swagger.NumberSchema)
    output3.asInstanceOf[swagger.ObjectSchema].getProperties.asScala shouldEqual Map("^.*$" -> new swagger.StringSchema)
  }

  "should convert dictionary schema properly (dict contains object and array of objects)" in {
    val innerInput = json.Schema.`object`(
      json.Schema.`object`.Field("sampleString", json.Schema.string, true),
      json.Schema.`object`.Field("sampleInteger", json.Schema.integer, false),
      json.Schema.`object`.Field("sampleBoolean", json.Schema.boolean, true)
    )

    val arrayInnerInput = new json.Schema.array[AnyRef, List](innerInput)

    val input      = new json.Schema.dictionary(innerInput)
    val arrayInput = new json.Schema.dictionary(arrayInnerInput)

    val output = converter.convert(input)
    output.asInstanceOf[swagger.ObjectSchema].getProperties.asScala shouldEqual Map("^.*$" -> converter.convert(innerInput))

    val arrayOutput = converter.convert(arrayInput)
    arrayOutput.asInstanceOf[swagger.ObjectSchema].getProperties.asScala shouldEqual Map("^.*$" -> converter.convert(arrayInnerInput))
  }

  "should convert enum schema properly" in {
    val input1 = json.Schema.`enum`(json.Schema.string, Set(Value.str("s1"), Value.str("s2"), Value.str("s3")))
    val input2 = json.Schema.`enum`(json.Schema.integer, Set(Value.num(1), Value.num(10), Value.num(30)))
    val input3 = json.Schema.`enum`(json.Schema.number[Double], Set(Value.num(1.123), Value.num(10.1), Value.num(3.2)))

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)
    val output3 = converter.convert(input3)

    output1 should haveClass[swagger.StringSchema]
    output2 should haveClass[swagger.IntegerSchema]
    output3 should haveClass[swagger.NumberSchema]

    output1.getEnum.asScala.toList should containTheSameElementsAs(List("s1", "s2", "s3"))
    output2.getEnum.asScala.toList should containTheSameElementsAs(List(1, 10, 30))
    output3.getEnum.asScala.toList should containTheSameElementsAs(
      List(new JavaBigDecimal("1.123"), new JavaBigDecimal("10.1"), new JavaBigDecimal("3.2"))
    )
  }

  "should convert integer schema properly" in {
    val input = json.Schema.integer

    val output = converter.convert(input)

    output shouldEqual new swagger.IntegerSchema
  }

  "should convert not schema properly" in {
    val input = json.Schema.not(json.Schema.integer)

    val output = converter.convert(input)

    output shouldEqual new swagger.Schema[Unit]()
  }

  "should convert number schema properly" in {
    val input1 = json.Schema.number[Double]
    val input2 = json.Schema.number[BigDecimal]
    val input3 = json.Schema.number[Float]

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)
    val output3 = converter.convert(input3)

    output1 shouldEqual new swagger.NumberSchema
    output2 shouldEqual new swagger.NumberSchema
    output3 shouldEqual new swagger.NumberSchema
  }

  "should convert string schema properly" in {
    val input1 = json.Schema.string
    val input2 = json.Schema.string(json.Schema.string.Format.uuid)

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)

    output1 shouldEqual new swagger.StringSchema
    output2 should haveClass[swagger.StringSchema]
    output2.getFormat shouldEqual json.Schema.string.Format.uuid.toString
  }

  "should convert object schema properly" in {
    val input = json.Schema.`object`(
      json.Schema.`object`.Field("sampleString", json.Schema.string, true),
      json.Schema.`object`.Field("sampleInteger", json.Schema.integer, false),
      json.Schema.`object`.Field("sampleBoolean", json.Schema.boolean, true)
    )

    val output = converter.convert(input)

    output should haveClass[swagger.ObjectSchema]
    output.asInstanceOf[swagger.ObjectSchema].getRequired.asScala should
      containTheSameElementsAs(List("sampleString", "sampleBoolean"))
    val outputProperties = output.getProperties.asScala
    outputProperties("sampleString") should haveClass[swagger.StringSchema]
    outputProperties("sampleInteger") should haveClass[swagger.IntegerSchema]
    outputProperties("sampleBoolean") should haveClass[swagger.BooleanSchema]
  }

  "should convert object schema properly (object contains other object and array and dict inside)" in {
    val innerObject = json.Schema.`object`(
      json.Schema.`object`.Field("sampleString", json.Schema.string, true),
      json.Schema.`object`.Field("sampleInteger", json.Schema.integer, false),
      json.Schema.`object`.Field("sampleBoolean", json.Schema.boolean, true)
    )

    val innerArray          = json.Schema.array(new json.Schema.dictionary(json.Schema.string))
    val innerArrayOfObjects = json.Schema.array(innerObject)

    val innerDict                 = new json.Schema.dictionary(json.Schema.string)
    val innerDictOfArray          = new json.Schema.dictionary(innerArray)
    val innerDictOfArrayOfObjects = new json.Schema.dictionary(innerArrayOfObjects)
    val innerDictOfObjects        = new json.Schema.dictionary(innerObject)

    val input = json.Schema.`object`(
      json.Schema.`object`.Field("sampleString", json.Schema.string, true),
      json.Schema.`object`.Field("sampleNumber", json.Schema.number[Double], true),
      json.Schema.`object`.Field("innerObject", innerObject, true),
      json.Schema.`object`.Field("innerArray", innerArray, false),
      json.Schema.`object`.Field("innerArrayOfObjects", innerArrayOfObjects, true),
      json.Schema.`object`.Field("innerDict", innerDict, false),
      json.Schema.`object`.Field("innerDictOfArray", innerDictOfArray, false),
      json.Schema.`object`.Field("innerDictOfArrayOfObjects", innerDictOfArrayOfObjects, false),
      json.Schema.`object`.Field("innerDictOfObjects", innerDictOfObjects, false),
    )

    val output = converter.convert(input)

    output should haveClass[swagger.ObjectSchema]
    output.asInstanceOf[swagger.ObjectSchema].getRequired.asScala should
      containTheSameElementsAs(List("sampleString", "sampleNumber", "innerObject", "innerArrayOfObjects"))
    val outputProperties = output.getProperties.asScala
    outputProperties("sampleString") should haveClass[swagger.StringSchema]
    outputProperties("sampleNumber") should haveClass[swagger.NumberSchema]
    outputProperties("innerArray") shouldEqual converter.convert(innerArray)
    outputProperties("innerArrayOfObjects") shouldEqual converter.convert(innerArrayOfObjects)
    outputProperties("innerDict") shouldEqual converter.convert(innerDict)
    outputProperties("innerDictOfArray") shouldEqual converter.convert(innerDictOfArray)
    outputProperties("innerDictOfArrayOfObjects") shouldEqual converter.convert(innerDictOfArrayOfObjects)
    outputProperties("innerDictOfObjects") shouldEqual converter.convert(innerDictOfObjects)
  }

  "should convert oneof schema properly" in {
    val inner1 = json.Schema.boolean
    val inner2 = json.Schema.number[BigDecimal]

    val input1 = new json.Schema.oneof(Set(inner1, inner2))
    val input2 = new json.Schema.oneof(Set(inner2, inner1))
    val input3 = new json.Schema.oneof(Set.empty)

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)
    val output3 = converter.convert(input3)

    output1 shouldEqual converter.convert(inner1)
    output2 shouldEqual converter.convert(inner2)
    output3 shouldEqual new swagger.Schema[Unit]()
  }

  "should convert ref schema properly" in {
    val input = json.Schema.ref("ref")

    val output = converter.convert(input)

    output shouldEqual new swagger.Schema[Unit]()
  }

  "should convert valueclass schema properly" in {
    val inner1 = json.Schema.boolean
    val inner2 = json.Schema.number[BigDecimal]
    val inner3 = json.Schema.string

    val input1 = json.Schema.`value-class`[Float, Boolean](inner1)
    val input2 = json.Schema.`value-class`[String, BigDecimal](inner2)
    val input3 = json.Schema.`value-class`[Float, String](inner3)

    val output1 = converter.convert(input1)
    val output2 = converter.convert(input2)
    val output3 = converter.convert(input3)

    output1 shouldEqual converter.convert(inner1)
    output2 shouldEqual converter.convert(inner2)
    output3 shouldEqual converter.convert(inner3)
  }

}
