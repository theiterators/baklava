package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import pl.iterators.baklava.BaklavaViewFilter._
import sttp.model.StatusCode

class BaklavaViewSpec extends AnyFunSpec with Matchers {

  private def callAt(symbolicPath: String, tags: String*) = BaklavaSerializableCall(
    BaklavaRequestContextSerializable(
      symbolicPath = symbolicPath,
      path = symbolicPath,
      pathDescription = None,
      pathSummary = None,
      method = None,
      operationDescription = None,
      operationSummary = None,
      operationId = None,
      operationTags = tags,
      securitySchemes = Seq.empty,
      bodySchema = None,
      headersSeq = Seq.empty,
      pathParametersSeq = Seq.empty,
      queryParametersSeq = Seq.empty,
      responseDescription = None,
      responseHeaders = Seq.empty
    ),
    BaklavaResponseContextSerializable(
      protocol = BaklavaHttpProtocol("HTTP/1.1"),
      status = StatusCode.Ok,
      headers = Seq.empty,
      requestContentType = None,
      responseContentType = None,
      bodySchema = None
    )
  )

  private val pets    = callAt("/pets/{id}", "public", "Pets")
  private val loggers = callAt("/admin/loggers/{name}", "Admin")
  private val health  = callAt("/health")

  describe("BaklavaViewFilter.parse") {
    it("parses every filter kind") {
      parse("all") shouldBe Right(All)
      parse("tag-includes:public") shouldBe Right(TagIncludes("public"))
      parse(" tag-excludes: Admin ") shouldBe Right(TagExcludes("Admin"))
      parse("path-includes:^/pets") shouldBe Right(PathIncludes("^/pets"))
      parse("path-excludes:^/admin/") shouldBe Right(PathExcludes("^/admin/"))
    }

    it("keeps colons inside the argument, so tags and regexes may contain them") {
      parse("tag-includes:scope:read") shouldBe Right(TagIncludes("scope:read"))
      parse("path-includes:^/v1/(?<id>[a-z]+):action") shouldBe Right(PathIncludes("^/v1/(?<id>[a-z]+):action"))
    }

    it("rejects unknown kinds, missing arguments and bad regexes") {
      parse("by-moon-phase:full").left.toOption.get should include("unknown view filter 'by-moon-phase:full'")
      parse("tag-includes:").left.toOption.get should include("unknown view filter")
      parse("all:really").left.toOption.get should include("unknown view filter")
      parse("path-includes:^/pets(").left.toOption.get should include("invalid regex")
    }
  }

  describe("BaklavaViewFilter.matches") {
    it("matches tags exactly and paths by regex search on the symbolic path") {
      TagIncludes("public").matches(pets) shouldBe true
      TagIncludes("public").matches(loggers) shouldBe false
      TagExcludes("Admin").matches(loggers) shouldBe false
      TagExcludes("Admin").matches(health) shouldBe true
      PathIncludes("^/admin/").matches(loggers) shouldBe true
      PathIncludes("^/admin/").matches(pets) shouldBe false
      PathExcludes("^/admin/").matches(pets) shouldBe true
      PathExcludes("^/admin/").matches(loggers) shouldBe false
      PathIncludes("\\{id\\}").matches(pets) shouldBe true
    }
  }

  describe("BaklavaView") {
    it("selects the calls every filter accepts") {
      val view = BaklavaView("public-pets", Seq(TagIncludes("public"), PathExcludes("^/admin/")))
      view.select(Seq(pets, loggers, health)) shouldBe Seq(pets)
      BaklavaView("all", Seq(All)).select(Seq(pets, loggers, health)) shouldBe Seq(pets, loggers, health)
    }

    it("writes under views/<name> of the output root") {
      BaklavaView("client", Seq(All)).outputRoot("target/baklava") shouldBe "target/baklava/views/client"
    }
  }

  describe("BaklavaView.fromConfig") {
    it("yields no views when the key is absent or blank") {
      BaklavaView.fromConfig(Map.empty) shouldBe Right(Seq.empty)
      BaklavaView.fromConfig(Map("views" -> "  \n")) shouldBe Right(Seq.empty)
    }

    it("parses a single filter, a filter list, and a view without a filter") {
      val parsed = BaklavaView.fromConfig(
        Map(
          "views" -> """[
            { "name": "client", "filter": "path-excludes:^/admin/" },
            { "name": "public", "filter": ["tag-includes:public", "path-excludes:^/admin/"] },
            { "name": "full" }
          ]"""
        )
      )
      parsed shouldBe Right(
        Seq(
          BaklavaView("client", Seq(PathExcludes("^/admin/"))),
          BaklavaView("public", Seq(TagIncludes("public"), PathExcludes("^/admin/"))),
          BaklavaView("full", Seq(All))
        )
      )
    }

    it("reports what is wrong with the configuration") {
      def error(views: String): String = BaklavaView.fromConfig(Map("views" -> views)).left.toOption.get

      error("{") should include("not valid JSON")
      error("""{ "name": "client" }""") should include("expected a JSON array")
      error("""[{ "filter": "all" }]""") should include("needs a string \"name\"")
      error("""[{ "name": "../etc", "filter": "all" }]""") should include("must match")
      error("""[{ "name": "client", "filter": 42 }]""") should include("must be a string or an array of strings")
      error("""[{ "name": "client", "filter": ["all", 42] }]""") should include("filter entries must be strings")
      error("""[{ "name": "client", "filter": "nope" }]""") should include("unknown view filter 'nope'")
      error("""[{ "name": "client" }, { "name": "client" }]""") should include("duplicated: client")
    }
  }
}
