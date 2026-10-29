package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import pl.iterators.baklava.BaklavaSerialize.serializableCallCodec

import java.nio.file.{Files, Path}

class BaklavaCallStoreSpec extends AnyFunSpec with Matchers {

  private def freshDir(): Path = Files.createTempDirectory("baklava-store")

  /** Puts `call` into the persistent store under `suite`, as an earlier run would have. */
  private def seedStore(store: BaklavaCallStore, suite: String, call: BaklavaSerializableCall): Unit = {
    store.write(suite, call).get
    store.mergeRunIntoStore().get
    ()
  }

  private def paths(calls: Seq[BaklavaSerializableCall]): Seq[String] = calls.map(_.request.symbolicPath)

  /** A real class whose name stands in for a suite that still exists. */
  private val RealSuite = classOf[BaklavaCallStore].getName

  /** A class loader that loads exactly `names` (which must be real classes: the JVM rejects a class returned under another name), fails to
    * link `linkageErrors`, and has never heard of anything else.
    */
  private def loaderKnowing(names: Set[String], linkageErrors: Set[String] = Set.empty): ClassLoader =
    new ClassLoader(getClass.getClassLoader) {
      override def loadClass(name: String, resolve: Boolean): Class[_] =
        if (linkageErrors(name)) throw new NoClassDefFoundError(name)
        else if (names(name)) super.loadClass(name, resolve)
        else throw new ClassNotFoundException(name)
    }

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

  describe("BaklavaCallStore merge, list and clear") {

    it("merge replaces a suite that re-ran and keeps suites that did not") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      seedStore(store, "com.acme.PetsSpec", CallFixtures.callAt("/pets", "/pets"))
      seedStore(store, "com.acme.UsersSpec", CallFixtures.callAt("/users", "/users"))
      store.write("com.acme.PetsSpec", CallFixtures.callAt("/pets/{id}", "/pets/1")).get
      store.mergeRunIntoStore().get shouldBe Seq("com.acme.PetsSpec")
      paths(store.listStoredCalls()) shouldBe Seq("/pets/{id}", "/users")
      Files.exists(tmp.resolve("calls").resolve("com.acme.PetsSpec")) shouldBe false // suite directory moved, not copied
    }

    it("merge ignores a staging suite directory without json files") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      seedStore(store, "com.acme.PetsSpec", CallFixtures.callAt("/pets", "/pets"))
      Files.createDirectories(tmp.resolve("calls").resolve("com.acme.PetsSpec"))
      store.mergeRunIntoStore().get shouldBe Nil
      paths(store.listStoredCalls()) shouldBe Seq("/pets")
    }

    it("merge and list are no-ops on a base directory that does not exist yet") {
      val store = new BaklavaCallStore(freshDir().resolve("missing"))
      store.mergeRunIntoStore().get shouldBe Nil
      store.listStoredCalls() shouldBe Nil
      store.storedSuites() shouldBe Nil
      store.clearStaging().get
    }

    it("merge leaves legacy flat files out of the store; clearStaging removes them; listStagedCalls still sees them") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      Files.createDirectories(tmp.resolve("calls"))
      Files.write(tmp.resolve("calls").resolve("legacy.json"), writeToArray(CallFixtures.callAt("/old", "/old")))
      paths(store.listStagedCalls()) shouldBe Seq("/old")
      store.mergeRunIntoStore().get shouldBe Nil
      store.listStoredCalls() shouldBe Nil
      store.clearStaging().get
      Files.exists(tmp.resolve("calls")) shouldBe false
    }

    it("listStoredCalls dedupes identical calls captured by two suites and is deterministic") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      store.write("com.acme.B", CallFixtures.callAt("/shared", "/shared")).get
      store.write("com.acme.A", CallFixtures.callAt("/shared", "/shared")).get
      store.write("com.acme.A", CallFixtures.callAt("/a", "/a")).get
      store.mergeRunIntoStore().get shouldBe Seq("com.acme.A", "com.acme.B")
      paths(store.listStoredCalls()).sorted shouldBe Seq("/a", "/shared")
      store.listStoredCalls() shouldBe store.listStoredCalls()
    }

    it("listStoredCalls skips a file that does not decode") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      seedStore(store, "com.acme.PetsSpec", CallFixtures.callAt("/pets", "/pets"))
      Files.write(tmp.resolve("store").resolve("com.acme.PetsSpec").resolve("broken.json"), "{".getBytes("UTF-8"))
      paths(store.listStoredCalls()) shouldBe Seq("/pets")
    }
  }

  describe("BaklavaCallStore.pruneMissingSuites") {

    it("removes a stored suite whose class is gone and keeps the ones that resolve") {
      val store = new BaklavaCallStore(freshDir())
      seedStore(store, "com.acme.Gone", CallFixtures.callAt("/gone", "/gone"))
      seedStore(store, RealSuite, CallFixtures.callAt("/kept", "/kept"))
      store.pruneMissingSuites(Nil, Seq(loaderKnowing(Set(RealSuite)))) shouldBe BaklavaCallStore.Pruned(Seq("com.acme.Gone"))
      store.storedSuites() shouldBe Seq(RealSuite)
    }

    it("tries every loader before declaring a class gone") {
      val store = new BaklavaCallStore(freshDir())
      seedStore(store, RealSuite, CallFixtures.callAt("/kept", "/kept"))
      seedStore(store, "com.acme.Gone", CallFixtures.callAt("/gone", "/gone"))
      val outcome = store.pruneMissingSuites(Nil, Seq(loaderKnowing(Set.empty), loaderKnowing(Set(RealSuite))))
      outcome shouldBe BaklavaCallStore.Pruned(Seq("com.acme.Gone"))
    }

    it("treats a linkage error as an existing class") {
      val store = new BaklavaCallStore(freshDir())
      seedStore(store, "com.acme.Broken", CallFixtures.callAt("/b", "/b"))
      seedStore(store, RealSuite, CallFixtures.callAt("/kept", "/kept"))
      val loaders = Seq(loaderKnowing(Set(RealSuite), linkageErrors = Set("com.acme.Broken")))
      store.pruneMissingSuites(Nil, loaders) shouldBe BaklavaCallStore.Pruned(Nil)
    }

    it("never touches the _unscoped suite") {
      val store = new BaklavaCallStore(freshDir())
      seedStore(store, BaklavaCallStore.UnscopedSuite, CallFixtures.callAt("/u", "/u"))
      seedStore(store, RealSuite, CallFixtures.callAt("/kept", "/kept"))
      store.pruneMissingSuites(Nil, Seq(loaderKnowing(Set(RealSuite)))) shouldBe BaklavaCallStore.Pruned(Nil)
      store.storedSuites() shouldBe Seq(BaklavaCallStore.UnscopedSuite, RealSuite)
    }

    it("is skipped when a suite merged in this run cannot be loaded") {
      val store = new BaklavaCallStore(freshDir())
      seedStore(store, "com.acme.Fresh", CallFixtures.callAt("/f", "/f"))
      seedStore(store, "com.acme.Old", CallFixtures.callAt("/o", "/o"))
      val outcome = store.pruneMissingSuites(Seq("com.acme.Fresh"), Seq(loaderKnowing(Set.empty)))
      outcome shouldBe a[BaklavaCallStore.Skipped]
      outcome.asInstanceOf[BaklavaCallStore.Skipped].reason should include("com.acme.Fresh")
      store.storedSuites() shouldBe Seq("com.acme.Fresh", "com.acme.Old")
    }

    it("is skipped when none of the stored suites can be loaded") {
      val store = new BaklavaCallStore(freshDir())
      seedStore(store, "com.acme.A", CallFixtures.callAt("/a", "/a"))
      seedStore(store, "com.acme.B", CallFixtures.callAt("/b", "/b"))
      store.pruneMissingSuites(Nil, Seq(loaderKnowing(Set.empty))) shouldBe BaklavaCallStore.Skipped(
        "none of the 2 stored suites can be loaded"
      )
      store.storedSuites() shouldBe Seq("com.acme.A", "com.acme.B")
    }

    it("on an empty store is Pruned(Nil)") {
      new BaklavaCallStore(freshDir()).pruneMissingSuites(Nil, Seq(loaderKnowing(Set.empty))) shouldBe BaklavaCallStore.Pruned(Nil)
    }
  }
}
