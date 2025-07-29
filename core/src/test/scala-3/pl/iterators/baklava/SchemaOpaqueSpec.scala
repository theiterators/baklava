package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.kebs.opaque.Opaque
import pl.iterators.kebs.baklava.schema.KebsBaklavaSchema

import java.util.UUID

opaque type OInt = Int
object OInt extends Opaque[OInt, Int]

opaque type OString = String
object OString extends Opaque[OString, String]

opaque type OUUID = UUID
object OUUID extends Opaque[OUUID, UUID]

class SchemaOpaqueSpec extends AnyFunSpec with Matchers with KebsBaklavaSchema {

  describe("Schema opaque derivation") {

    it("for OInt") {
      val derived  = implicitly[Schema[OInt]]
      val expected = Schema.intSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[OInt]") {
      val derived  = implicitly[Schema[Option[OInt]]]
      val expected = Schema.optionSchema(Schema.intSchema)
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for OString") {
      val derived  = implicitly[Schema[OString]]
      val expected = Schema.stringSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[OString]") {
      val derived  = implicitly[Schema[Option[OString]]]
      val expected = Schema.optionSchema(Schema.stringSchema)
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for OUUID") {
      val derived  = implicitly[Schema[OUUID]]
      val expected = Schema.uuidSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[OUUID]") {
      val derived  = implicitly[Schema[Option[OUUID]]]
      val expected = Schema.optionSchema(Schema.uuidSchema)
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    case class TestClass(x: OInt, y: Option[OString], z: Option[OUUID])

    it("for TestClass") {
      val derived = implicitly[Schema[TestClass]]

      val expected = new Schema[TestClass] {
        val className: String      = "TestClass"
        val `type`: SchemaType     = SchemaType.ObjectType
        val format: Option[String] = None
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
