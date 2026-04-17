package pl.iterators.baklava.tsrest

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.*

class BaklavaDslFormatterTsRestSpec extends AnyFunSpec with Matchers {

  private val generator = new BaklavaDslFormatterTsRest

  describe("zod(): schema.description rendering") {

    it("escapes backslashes inside .describe(\"...\") so they survive TypeScript string interpretation") {
      val schema = stringSchema(description = Some("""C:\Users\test""" + "\n" + "next line"))
      val out    = generator.zod(schema)
      // Input backslashes become `\\` (pair). Input newline becomes `\n` (one backslash + n).
      // TypeScript parses "C:\\Users\\test\nnext line" back to the original string.
      out should include(""".describe("C:\\Users\\test\nnext line")""")
      // Sanity: the emitted TS source should not contain a real newline inside the string literal.
      val emittedString = out.substring(out.indexOf(".describe("))
      emittedString should not contain '\n'
    }

    it("escapes double quotes inside .describe(\"...\")") {
      val schema = stringSchema(description = Some("""He said "hi""""))
      val out    = generator.zod(schema)
      out should include(""".describe("He said \"hi\"")""")
    }
  }

  describe("zod(): z.enum values") {

    it("escapes embedded double quotes in enum literals") {
      val schema = stringSchema(enumValues = Some(Set("foo", "weird\"quote")))
      val out    = generator.zod(schema)
      // Both values quoted; the " inside is escaped as \"
      out should startWith("z.enum([")
      out should include(""""weird\"quote"""")
      out should include(""""foo"""")
    }

    it("escapes backslashes in enum literals") {
      val schema = stringSchema(enumValues = Some(Set("""C:\tmp""")))
      val out    = generator.zod(schema)
      out should include(""""C:\\tmp"""")
    }

    it("produces deterministic (sorted) enum order") {
      val a = generator.zod(stringSchema(enumValues = Some(Set("c", "a", "b"))))
      val b = generator.zod(stringSchema(enumValues = Some(Set("b", "c", "a"))))
      a shouldBe b
      a should include("""z.enum(["a","b","c"])""")
    }
  }

  describe("zod(): object property keys") {

    it("quotes object property keys so hyphens/digits/reserved words are valid TypeScript") {
      val schema = objectSchema(
        Map(
          "content-type" -> stringSchema(),
          "2fa"          -> stringSchema(),
          "okName"       -> stringSchema()
        )
      )
      val out = generator.zod(schema)
      out should include(""""content-type": z.string()""")
      out should include(""""2fa": z.string()""")
      out should include(""""okName": z.string()""")
    }

    it("sorts object properties alphabetically for deterministic output") {
      val schemaA = objectSchema(Map("b" -> stringSchema(), "a" -> stringSchema(), "c" -> stringSchema()))
      val schemaB = objectSchema(Map("c" -> stringSchema(), "a" -> stringSchema(), "b" -> stringSchema()))
      generator.zod(schemaA) shouldBe generator.zod(schemaB)

      val out         = generator.zod(schemaA)
      val positionOfA = out.indexOf("\"a\"")
      val positionOfB = out.indexOf("\"b\"")
      val positionOfC = out.indexOf("\"c\"")
      positionOfA should (be < positionOfB and be < positionOfC)
      positionOfB should be < positionOfC
    }
  }

  describe("collapseZodUnion") {

    it("preserves non-object variants alongside object variants (regression)") {
      val out = generator.collapseZodUnion(Seq("z.string()", "z.object({foo: z.string()})"))
      out should startWith("z.union([")
      out should include("z.string()")
      out should include("z.object({foo: z.string()})")
    }

    it("emits a single entry without wrapping when only one distinct variant is present") {
      generator.collapseZodUnion(Seq("z.string()")) shouldBe "z.string()"
      // Duplicates collapse.
      generator.collapseZodUnion(Seq("z.string()", "z.string()")) shouldBe "z.string()"
    }

    it("emits z.undefined() on empty input") {
      generator.collapseZodUnion(Nil) shouldBe "z.undefined()"
    }
  }

  describe("contractNameFromSymbolicPath") {

    it("preserves existing non-collision behavior") {
      generator.contractNameFromSymbolicPath("/pets") shouldBe "pets"
      generator.contractNameFromSymbolicPath("/pets/{id}") shouldBe "pets---id"
      generator.contractNameFromSymbolicPath("/") shouldBe "root"
    }
  }

  private def stringSchema(description: Option[String] = None, enumValues: Option[Set[String]] = None): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "String",
      `type` = SchemaType.StringType,
      format = None,
      properties = Map.empty,
      items = None,
      `enum` = enumValues,
      required = true,
      additionalProperties = false,
      default = None,
      description = description
    )

  private def objectSchema(properties: Map[String, BaklavaSchemaSerializable]): BaklavaSchemaSerializable =
    BaklavaSchemaSerializable(
      className = "Object",
      `type` = SchemaType.ObjectType,
      format = None,
      properties = properties,
      items = None,
      `enum` = None,
      required = true,
      additionalProperties = false,
      default = None,
      description = None
    )
}
