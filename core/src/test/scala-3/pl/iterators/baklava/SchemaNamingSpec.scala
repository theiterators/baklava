package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

enum TrafficLight {
  case RedLight, GreenLight
}

class SchemaNamingSpec extends AnyFunSpec with Matchers {
  import SchemaNamingFixtures.*

  describe("SchemaDerivation with snake_case transforms") {
    import SnakeCaseSchemas.given

    it("transforms flat case class property names and keeps types") {
      val derived = implicitly[Schema[InnerPayload]]
      derived.properties.keySet shouldBe Set("inner_field", "some_count")
      derived.properties("inner_field").`type` shouldBe SchemaType.StringType
      derived.properties("some_count").`type` shouldBe SchemaType.IntegerType
    }

    it("does not transform the class name") {
      val derived = implicitly[Schema[InnerPayload]]
      derived.className shouldBe "InnerPayload"
    }

    it("keeps Option fields optional under transformed keys") {
      val derived = implicitly[Schema[OuterPayload]]
      derived.properties("last_name").required shouldBe false
      derived.properties("first_name").required shouldBe true
    }

    it("transforms nested case class property names") {
      val derived = implicitly[Schema[OuterPayload]]
      derived.properties("inner_payload").properties.keySet shouldBe Set("inner_field", "some_count")
    }

    it("transforms property names inside collection item schemas") {
      val derived = implicitly[Schema[OuterPayload]]
      derived.properties("tags_list").`type` shouldBe SchemaType.ArrayType
      derived.properties("tags_list").items.get.properties.keySet shouldBe Set("inner_field", "some_count")
    }

    it("transforms property names inside map value schemas") {
      val derived = implicitly[Schema[OuterPayload]]
      derived.properties("extra_data").additionalPropertiesSchema.get.properties.keySet shouldBe Set("inner_field", "some_count")
    }

    it("transforms sealed trait enum values") {
      val derived = implicitly[Schema[UserStatus]]
      derived.`enum` shouldBe Some(Set("active_user", "banned_user"))
      derived.className shouldBe "UserStatus"
    }

    it("transforms enum values of a sealed trait field") {
      val derived = implicitly[Schema[WithStatus]]
      derived.properties("current_status").`enum` shouldBe Some(Set("active_user", "banned_user"))
    }

    it("transforms Scala 3 enum values") {
      val derived = implicitly[Schema[TrafficLight]]
      derived.`enum` shouldBe Some(Set("red_light", "green_light"))
    }
  }

  describe("SchemaDerivation with a member-only transform") {
    import MemberOnlySnakeCaseSchemas.given

    it("transforms member names but leaves enum values untouched") {
      val derived = implicitly[Schema[WithStatus]]
      derived.properties("current_status").`enum` shouldBe Some(Set("ActiveUser", "BannedUser"))
    }
  }

  describe("default Schema derivation") {
    it("keeps identity naming for members and enum values") {
      val derived = implicitly[Schema[OuterPayload]]
      derived.properties.keySet shouldBe Set("firstName", "lastName", "innerPayload", "tagsList", "extraData")
      implicitly[Schema[UserStatus]].`enum` shouldBe Some(Set("ActiveUser", "BannedUser"))
    }
  }
}
