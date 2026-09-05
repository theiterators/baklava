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
      val expected = Schema.optionSchema[Int]
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for String") {
      val derived  = implicitly[Schema[String]]
      val expected = Schema.stringSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[String]") {
      val derived  = implicitly[Schema[Option[String]]]
      val expected = Schema.optionSchema[String]
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for UUID") {
      val derived  = implicitly[Schema[UUID]]
      val expected = Schema.uuidSchema
      SchemaCompare.assertSchemaFieldsEqual(derived, expected)
    }

    it("for Option[UUID]") {
      val derived  = implicitly[Schema[Option[UUID]]]
      val expected = Schema.optionSchema[UUID]
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
            "y" -> Schema.optionSchema[String],
            "z" -> Schema.optionSchema[UUID]
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

    it("for Map[String, Int] (object + additionalProperties value schema)") {
      val derived = implicitly[Schema[Map[String, Int]]]
      derived.`type` shouldBe SchemaType.ObjectType
      derived.className shouldBe "Map[String, Int]"
      derived.additionalProperties shouldBe true
      derived.additionalPropertiesSchema.map(_.`type`) shouldBe Some(SchemaType.IntegerType)
    }

    it("for Map[UUID, String] (a non-string key drops the key constraint, keeps the value schema)") {
      val derived = implicitly[Schema[Map[UUID, String]]]
      derived.`type` shouldBe SchemaType.ObjectType
      derived.additionalPropertiesSchema.map(_.`type`) shouldBe Some(SchemaType.StringType)
    }

    case class WithMap(id: Int, tags: Map[String, String])

    it("for a case class with a Map[String, String] field") {
      val derived = implicitly[Schema[WithMap]]
      val tags    = derived.properties("tags")
      tags.`type` shouldBe SchemaType.ObjectType
      tags.additionalProperties shouldBe true
      tags.additionalPropertiesSchema.map(_.`type`) shouldBe Some(SchemaType.StringType)
    }

    it("for Option[Map[String, Int]] (the value schema survives the Option wrapper)") {
      val derived = implicitly[Schema[Option[Map[String, Int]]]]
      derived.required shouldBe false
      derived.`type` shouldBe SchemaType.ObjectType
      derived.additionalPropertiesSchema.map(_.`type`) shouldBe Some(SchemaType.IntegerType)
    }
  }

  describe("nullable flag (regression for #131)") {

    case class WithOptional(id: Int, note: Option[String])

    it("marks Option schemas nullable and plain schemas not") {
      implicitly[Schema[Option[String]]].nullable shouldBe true
      implicitly[Schema[String]].nullable shouldBe false
    }

    it("marks Option fields of a derived case class nullable") {
      val derived = implicitly[Schema[WithOptional]]
      derived.properties("note").nullable shouldBe true
      derived.properties("id").nullable shouldBe false
    }

    it("survives withDescription and withDefault copies") {
      Schema.optionSchema[String].withDescription("d").nullable shouldBe true
      Schema.optionSchema[String].withDefault(Some("x")).nullable shouldBe true
    }

    it("flows into BaklavaSchemaSerializable") {
      BaklavaSchemaSerializable(Schema.optionSchema[String]).nullable shouldBe true
      BaklavaSchemaSerializable(Schema.stringSchema).nullable shouldBe false
    }
  }
}
