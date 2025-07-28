package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SchemaSpec extends AnyFunSpec with Matchers {

  def assertSchemaFieldsEqual[A, B](a: Schema[A], b: Schema[B]): Unit = {
    a.className shouldEqual b.className
    a.`type` shouldEqual b.`type`
    a.format shouldEqual b.format
    a.properties.keySet shouldEqual b.properties.keySet
    for (k <- a.properties.keys) {
      val aProp = a.properties(k)
      val bProp = b.properties(k)
      aProp.className shouldEqual bProp.className
      aProp.`type` shouldEqual bProp.`type`
    }
    a.items shouldEqual b.items
    a.`enum` shouldEqual b.`enum`
    a.required shouldEqual b.required
    a.additionalProperties shouldEqual b.additionalProperties
    a.description shouldEqual b.description
    a.default shouldEqual b.default
    ()
  }

  describe("Schema derivation") {

    it("for Int") {
      val derived  = implicitly[Schema[Int]]
      val expected = Schema.intSchema
      assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[Int]") {
      val derived  = implicitly[Schema[Option[Int]]]
      val expected = Schema.optionSchema(Schema.intSchema)
      assertSchemaFieldsEqual(derived, expected)
    }

    case class TestClass(x: Int, y: Option[String])

    it("for TestClass") {
      val derived = implicitly[Schema[TestClass]]

      val expected = new Schema[TestClass] {
        val className: String      = "TestClass"
        val `type`: SchemaType     = SchemaType.ObjectType
        val format: Option[String] = None
        val properties: Map[String, Schema[?]] =
          Map(
            "x" -> Schema.intSchema,
            "y" -> Schema.optionSchema(Schema.stringSchema)
          )
        val items: Option[Schema[?]]      = None
        val `enum`: Option[Set[String]]   = None
        val required: Boolean             = true
        val additionalProperties: Boolean = false
        val default: Option[TestClass]    = None
        val description: Option[String]   = None
      }

      assertSchemaFieldsEqual(derived, expected)
    }
  }
}
