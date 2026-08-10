package pl.iterators.baklava.tsrest

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*
import pl.iterators.baklava.tscommon.{TsZodDialect, TsZodRenderer}

// Regression for #131: nullability must reach zod modifiers, including array elements and record values.
class TsZodRendererNullableSpec extends AnyFunSpec with Matchers {

  private val renderer = new TsZodRenderer(TsZodDialect.tsRest)

  case class Widget(id: Int, note: Option[String])

  describe("nullable flag in zod rendering") {

    it("renders Option-derived properties as .nullish()") {
      val zod = renderer.zodDefinition(BaklavaSchemaSerializable(implicitly[Schema[Widget]]))
      zod should include(""""note": z.string().nullish()""")
      zod should include(""""id": z.number().int()""")
      zod should not include """"id": z.number().int().nullish()"""
    }

    it("renders optional-but-not-nullable properties as .optional()") {
      val schema = BaklavaSchemaSerializable(implicitly[Schema[Widget]])
        .copy(properties = Map("x" -> BaklavaSchemaSerializable(Schema.stringSchema).copy(required = false)))
      renderer.zodDefinition(schema) should include(""""x": z.string().optional()""")
    }

    it("renders required-but-nullable properties as .nullable()") {
      val schema = BaklavaSchemaSerializable(implicitly[Schema[Widget]])
        .copy(properties = Map("x" -> BaklavaSchemaSerializable(Schema.stringSchema).copy(nullable = true)))
      renderer.zodDefinition(schema) should include(""""x": z.string().nullable()""")
    }

    it("renders nullable array elements as z.array(inner.nullable())") {
      val zod = renderer.zodDefinition(BaklavaSchemaSerializable(implicitly[Schema[Seq[Option[String]]]]))
      zod should include("z.array(z.string().nullable())")
    }

    it("renders nullable record values as z.record(z.string(), inner.nullable())") {
      val zod = renderer.zodDefinition(BaklavaSchemaSerializable(implicitly[Schema[Map[String, Option[Int]]]]))
      zod should include("z.record(z.string(), z.number().int().nullable())")
    }
  }
}
