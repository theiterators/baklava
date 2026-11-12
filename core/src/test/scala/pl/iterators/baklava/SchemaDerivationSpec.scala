package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

object SchemaDerivationFixtures {
  // a wrapper with its own instance that needs the wrapped type's schema — like NonEmptyList[T] in user code
  final class Wrapper[T](val value: T)
  object Wrapper {
    implicit def wrapperSchema[T](implicit inner: Schema[T]): Schema[Wrapper[T]] =
      Schema.listSchema(inner).asInstanceOf[Schema[Wrapper[T]]]
  }
  final case class Wrapping(leaves: Wrapper[Leaf])
  final case class Leaf(id: UUID, label: String)
  final case class WithDefaults(name: String, count: Int = 3, tags: List[String] = List("a", "b"))
  final case class Page[T](items: List[T], total: Long = 0L)
  case object Marker
  final case class Holder(marker: Marker.type, id: UUID)

  sealed trait Shape
  final case class Circle(radius: Double)              extends Shape
  sealed trait Polygon                                 extends Shape
  final case class Square(side: Double)                extends Polygon
  final case class Rect(width: Double, height: Double) extends Polygon
  case object Dot                                      extends Shape

  // the same nested type reached through many fields and wrappers — the shape from #146 that pushed
  // magnolia's call-site expansion over -Xmax-inlines
  final case class Flag(key: String, enabled: Boolean, owner: Option[Leaf], children: List[Leaf])
  final case class AuditEntry(
      before: Option[Flag],
      after: Option[Flag],
      current: Flag,
      history: List[Flag],
      byKey: Map[String, Flag],
      related: Vector[Option[Flag]],
      shape: Shape
  )
}

class SchemaDerivationSpec extends AnyFunSpec with Matchers {
  import SchemaDerivationFixtures._

  private val baseLeafSchema: Schema[Leaf] = implicitly[Schema[Leaf]]

  // order-independent structural rendering so derivation backends can be compared exactly
  private def render(s: Schema[?]): String = {
    val props = s.properties.toList.sortBy(_._1).map { case (k, v) => s"$k=${render(v)}" }.mkString("{", ",", "}")
    val enm   = s.`enum`.map(_.toList.sorted.mkString("[", ",", "]")).getOrElse("-")
    val items = s.items.map(render).getOrElse("-")
    val ap    = s.additionalPropertiesSchema.map(render).getOrElse("-")
    s"${s.className}:${s.`type`}:${s.format.getOrElse("-")}:req=${s.required}:null=${s.nullable}:ap=${s.additionalProperties}/$ap" +
    s":default=${s.default.getOrElse("-")}:enum=$enm:items=$items:props=$props"
  }

  describe("derived case class schemas") {
    it("describe nested case classes field by field") {
      render(implicitly[Schema[Leaf]]) shouldBe render(new Schema[Leaf] {
        val className                   = "Leaf"
        val `type`                      = SchemaType.ObjectType
        val format                      = None
        val properties                  = Map[String, Schema[?]]("id" -> Schema.uuidSchema, "label" -> Schema.stringSchema)
        val items                       = None
        val `enum`                      = None
        val required                    = true
        val additionalProperties        = false
        val default: Option[Leaf]       = None
        val description: Option[String] = None
      })
    }

    it("attach constructor defaults to the field schemas") {
      val s = implicitly[Schema[WithDefaults]]
      s.properties("name").default shouldBe None
      s.properties("count").default shouldBe Some(3)
      s.properties("tags").default shouldBe Some(List("a", "b"))
    }

    it("use the unparameterised short name for generic case classes and keep their defaults") {
      val s = implicitly[Schema[Page[Leaf]]]
      s.className shouldBe "Page"
      render(s.properties("items")) shouldBe render(Schema.listSchema(implicitly[Schema[Leaf]]))
      s.properties("total").default shouldBe Some(0L)
    }

    it("treat case objects as empty objects") {
      val s = implicitly[Schema[Holder]]
      s.properties("marker").className shouldBe "Marker"
      s.properties("marker").`type` shouldBe SchemaType.ObjectType
      s.properties("marker").properties shouldBe empty
    }
  }

  describe("derived sealed trait schemas") {
    it("are string enums of the leaf subtype names, flattening nested sealed traits") {
      val s = implicitly[Schema[Shape]]
      s.className shouldBe "Shape"
      s.`type` shouldBe SchemaType.StringType
      s.`enum` shouldBe Some(Set("Circle", "Square", "Rect", "Dot"))
    }

    it("list the subtype names alphabetically, keeping generated docs stable") {
      implicitly[Schema[Shape]].`enum`.get.toList shouldBe List("Circle", "Dot", "Rect", "Square")
    }
  }

  describe("a DTO reached through many fields and wrappers") {
    it("derives the same schema at every occurrence") {
      val s    = implicitly[Schema[AuditEntry]]
      val flag = render(implicitly[Schema[Flag]])
      render(s.properties("current")) shouldBe flag
      s.properties("before").nullable shouldBe true
      render(s.properties("history").items.get) shouldBe flag
      render(s.properties("byKey").additionalPropertiesSchema.get) shouldBe flag
      render(s.properties("related").items.get) shouldBe render(Schema.optionSchema(implicitly[Schema[Flag]]))
      s.properties("shape").`enum` shouldBe Some(Set("Circle", "Square", "Rect", "Dot"))
    }
  }

  describe("one derivation") {
    it("derives a nested type once and shares that instance across fields and wrappers") {
      val s       = implicitly[Schema[AuditEntry]]
      val current = s.properties("current")
      s.properties("history").items.get should be theSameInstanceAs current
      s.properties("byKey").additionalPropertiesSchema.get should be theSameInstanceAs current
      s.properties("current").properties("owner").properties should be theSameInstanceAs
      s.properties("current").properties("children").items.get.properties
    }
  }

  describe("Schema.derived") {
    it("derives the same schema as the automatic instance") {
      render(Schema.derived[AuditEntry]) shouldBe render(implicitly[Schema[AuditEntry]])
    }
  }

  describe("custom wrapper instances") {
    it("receive the derived schema of the wrapped type") {
      render(implicitly[Schema[Wrapping]].properties("leaves")) shouldBe render(Schema.listSchema(implicitly[Schema[Leaf]]))
    }
  }

  describe("explicit derivation") {
    it("prefers a pinned instance in scope over re-deriving") {
      implicit val pinnedLeaf: Schema[Leaf] = baseLeafSchema.withDescription("pinned")
      implicitly[Schema[Flag]].properties("owner").description shouldBe Some("pinned")
    }
  }
}
