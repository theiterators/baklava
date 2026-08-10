package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SchemaNameTransformSpec extends AnyFunSpec with Matchers {

  describe("snakeCase") {
    it("transforms camelCase") {
      SchemaNameTransform.snakeCase("firstName") shouldBe "first_name"
    }

    it("transforms PascalCase") {
      SchemaNameTransform.snakeCase("AdminUser") shouldBe "admin_user"
    }

    it("keeps acronyms together") {
      SchemaNameTransform.snakeCase("HTTPStatus") shouldBe "http_status"
      SchemaNameTransform.snakeCase("myHTTPStatus") shouldBe "my_http_status"
    }

    it("splits before an uppercase letter following a digit-terminated word") {
      SchemaNameTransform.snakeCase("userId2") shouldBe "user_id2"
      SchemaNameTransform.snakeCase("value2X") shouldBe "value2_x"
    }

    it("leaves single words and already-snake_case names untouched") {
      SchemaNameTransform.snakeCase("name") shouldBe "name"
      SchemaNameTransform.snakeCase("first_name") shouldBe "first_name"
      SchemaNameTransform.snakeCase("a") shouldBe "a"
      SchemaNameTransform.snakeCase("") shouldBe ""
    }
  }

  describe("screamingSnakeCase") {
    it("transforms camelCase to upper snake") {
      SchemaNameTransform.screamingSnakeCase("activeUser") shouldBe "ACTIVE_USER"
      SchemaNameTransform.screamingSnakeCase("BannedUser") shouldBe "BANNED_USER"
    }
  }

  describe("kebabCase") {
    it("transforms camelCase to kebab") {
      SchemaNameTransform.kebabCase("firstName") shouldBe "first-name"
      SchemaNameTransform.kebabCase("myHTTPStatus") shouldBe "my-http-status"
    }
  }
}
