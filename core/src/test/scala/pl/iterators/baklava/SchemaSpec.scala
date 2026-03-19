package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class SchemaSpec extends AnyFunSpec with Matchers {

  describe("Schema derivation") {

    it("for Int") {
      val derived  = implicitly[Schema[Int]]
      val expected = Schema.intSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[Int]") {
      val derived  = implicitly[Schema[Option[Int]]]
      val expected = Schema.optionSchema(Schema.intSchema)
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for String") {
      val derived  = implicitly[Schema[String]]
      val expected = Schema.stringSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[String]") {
      val derived  = implicitly[Schema[Option[String]]]
      val expected = Schema.optionSchema(Schema.stringSchema)
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for UUID") {
      val derived  = implicitly[Schema[UUID]]
      val expected = Schema.uuidSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[UUID]") {
      val derived  = implicitly[Schema[Option[UUID]]]
      val expected = Schema.optionSchema(Schema.uuidSchema)
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    case class TestClass(x: Int, y: Option[String], z: Option[UUID])

    it("for TestClass") {
      val derived = implicitly[Schema[TestClass]]

      val expected = new Schema[TestClass] {
        val className: String                  = "TestClass"
        val `type`: SchemaType                 = SchemaType.ObjectType
        val format: Option[String]             = None
        val properties: Map[String, Schema[?]] =
          Map(
            "x" -> Schema.intSchema,
            "y" -> Schema.optionSchema(Schema.stringSchema),
            "z" -> Schema.optionSchema(Schema.uuidSchema)
          )
        val items: Option[Schema[?]]      = None
        val `enum`: Option[Set[String]]   = None
        val required: Boolean             = true
        val additionalProperties: Boolean = false
        val default: Option[TestClass]    = None
        val description: Option[String]   = None
      }

      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }
  }
}
