package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.{Files, Path}

class BaklavaCallStoreSpec extends AnyFunSpec with Matchers {

  private def freshDir(): Path = Files.createTempDirectory("baklava-store")

  describe("BaklavaCallStore.write") {

    it("writes a call under staging/<suite>/<sha256>.json") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      val file  = store.write("com.acme.PetsSpec", CallFixtures.callAt("/pets", "/pets")).get
      file.getParent shouldBe tmp.resolve("calls").resolve("com.acme.PetsSpec")
      file.getFileName.toString should fullyMatch regex "[A-Za-z0-9_-]{43}\\.json"
      Files.exists(file) shouldBe true
    }

    it("names the file by content hash so identical calls within a suite collapse to one file") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      val a     = store.write("com.acme.PetsSpec", CallFixtures.callAt("/pets", "/pets")).get
      val b     = store.write("com.acme.PetsSpec", CallFixtures.callAt("/pets", "/pets")).get
      a shouldBe b
      Files.list(a.getParent).count() shouldBe 1
    }
  }

  describe("BaklavaCallStore.suiteName") {
    it("strips a trailing $ from object suite names") {
      BaklavaCallStore.suiteName(CallFixtures.getClass) shouldBe "pl.iterators.baklava.CallFixtures"
      BaklavaCallStore.suiteName(classOf[BaklavaCallStoreSpec]) shouldBe "pl.iterators.baklava.BaklavaCallStoreSpec"
    }
  }
}
