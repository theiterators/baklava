package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.util.Base64

class SecurityContributionSpec extends AnyFunSpec with Matchers {

  import BaklavaTestFrameworkDsl.{securityContribution, appendCookie}

  describe("securityContribution") {

    it("HttpBearer injects an Authorization: Bearer header, no cookie, no query") {
      val c = securityContribution(AppliedSecurity(HttpBearer(), Map("token" -> "abc.def.ghi")))
      c.headers shouldBe Map("Authorization" -> "Bearer abc.def.ghi")
      c.cookie shouldBe None
      c.queryParameters shouldBe empty
    }

    it("HttpBasic injects base64(id:secret) in the Authorization header") {
      val c       = securityContribution(AppliedSecurity(HttpBasic(), Map("id" -> "alice", "secret" -> "s3cr3t")))
      val encoded = Base64.getEncoder.encodeToString("alice:s3cr3t".getBytes(StandardCharsets.UTF_8))
      c.headers shouldBe Map("Authorization" -> s"Basic $encoded")
      c.cookie shouldBe None
      c.queryParameters shouldBe empty
    }

    it("ApiKeyInHeader injects the named header") {
      val c = securityContribution(AppliedSecurity(ApiKeyInHeader("X-Api-Key"), Map("apiKey" -> "k-123")))
      c.headers shouldBe Map("X-Api-Key" -> "k-123")
      c.cookie shouldBe None
      c.queryParameters shouldBe empty
    }

    it("ApiKeyInQuery contributes the key as a query parameter") {
      val c = securityContribution(AppliedSecurity(ApiKeyInQuery("api_key"), Map("apiKey" -> "k-456")))
      c.headers shouldBe empty
      c.cookie shouldBe None
      c.queryParameters shouldBe Map("api_key" -> Seq("k-456"))
    }

    it("ApiKeyInCookie contributes the key as a cookie segment") {
      val c = securityContribution(AppliedSecurity(ApiKeyInCookie("session"), Map("apiKey" -> "abc")))
      c.headers shouldBe empty
      c.cookie shouldBe Some("session" -> "abc")
      c.queryParameters shouldBe empty
    }

    it("MutualTls contributes nothing (transport-level)") {
      securityContribution(AppliedSecurity(MutualTls(), Map.empty)) shouldBe
      SecurityContribution(Map.empty, None, Map.empty)
    }

    it("OpenIdConnectInBearer injects Authorization: Bearer") {
      val c = securityContribution(AppliedSecurity(OpenIdConnectInBearer("https://issuer"), Map("token" -> "oidc-token")))
      c.headers shouldBe Map("Authorization" -> "Bearer oidc-token")
      c.cookie shouldBe None
    }

    it("OpenIdConnectInCookie contributes a named cookie") {
      val c = securityContribution(AppliedSecurity(OpenIdConnectInCookie("https://issuer"), Map("name" -> "id_token", "token" -> "oidc")))
      c.headers shouldBe empty
      c.cookie shouldBe Some("id_token" -> "oidc")
    }

    it("OAuth2InBearer injects Authorization: Bearer") {
      val flows = OAuthFlows()
      val c     = securityContribution(AppliedSecurity(OAuth2InBearer(flows), Map("token" -> "oauth-token")))
      c.headers shouldBe Map("Authorization" -> "Bearer oauth-token")
    }

    it("OAuth2InCookie contributes a named cookie") {
      val flows = OAuthFlows()
      val c     = securityContribution(AppliedSecurity(OAuth2InCookie(flows), Map("name" -> "auth", "token" -> "oauth")))
      c.cookie shouldBe Some("auth" -> "oauth")
    }

    it("NoopSecurity contributes nothing") {
      securityContribution(AppliedSecurity(NoopSecurity, Map.empty)) shouldBe
      SecurityContribution(Map.empty, None, Map.empty)
    }
  }

  describe("appendCookie") {

    it("creates a Cookie header when none exists") {
      appendCookie(Map.empty, "session" -> "abc") shouldBe Map("Cookie" -> "session=abc")
    }

    it("concatenates to an existing Cookie header with `; ` separator") {
      appendCookie(Map("Cookie" -> "foo=1"), "session" -> "abc") shouldBe
      Map("Cookie" -> "foo=1; session=abc")
    }

    it("matches the Cookie header case-insensitively and collapses to a single canonical Cookie key") {
      appendCookie(Map("cookie" -> "foo=1"), "session" -> "abc") shouldBe
      Map("Cookie" -> "foo=1; session=abc")
    }

    it("preserves unrelated headers") {
      appendCookie(Map("Authorization" -> "Bearer x"), "session" -> "abc") shouldBe
      Map("Authorization" -> "Bearer x", "Cookie" -> "session=abc")
    }
  }
}
