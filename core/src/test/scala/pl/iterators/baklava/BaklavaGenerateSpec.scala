package pl.iterators.baklava

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

class BaklavaGenerateSpec extends AnyFunSpec with Matchers {

  private class RecordingFormatter extends BaklavaDslFormatter {
    var invocations: List[(String, Seq[BaklavaSerializableCall])]                                                   = Nil
    override def create(config: Map[String, String], calls: Seq[BaklavaSerializableCall], outputRoot: String): Unit =
      invocations = invocations :+ (outputRoot -> calls)
  }

  private val call      = callAt("/pets/{id}", "/pets/1", tags = Seq("public"))
  private val adminCall = callAt("/admin/loggers/{name}", "/admin/loggers/root", tags = Seq("Admin"))

  private def callAt(symbolicPath: String, path: String, tags: Seq[String]) = CallFixtures.callAt(symbolicPath, path, tags)

  private def captureStdErr[T](body: => T): (T, String) = {
    val buffer   = new ByteArrayOutputStream()
    val original = System.err
    System.setErr(new PrintStream(buffer, true, "UTF-8"))
    try {
      val result = body
      (result, new String(buffer.toByteArray, StandardCharsets.UTF_8))
    } finally System.setErr(original)
  }

  describe("BaklavaGenerate.generate") {

    it("skips formatters when zero calls were captured, so existing output is not overwritten") {
      val formatter     = new RecordingFormatter
      val (ran, stderr) = captureStdErr(BaklavaGenerate.generate(Map.empty, Seq.empty, Seq(formatter)))

      ran shouldBe false
      formatter.invocations shouldBe empty
      stderr should include("0")
      stderr should include("testFull")
    }

    it("runs formatters when calls were captured") {
      val formatter = new RecordingFormatter
      val ran       = BaklavaGenerate.generate(Map("k" -> "v"), Seq(call), Seq(formatter))

      ran shouldBe true
      formatter.invocations shouldBe List("target/baklava" -> Seq(call))
    }

    it("runs every formatter again per view, with that view's calls under target/baklava/views/<name>") {
      val formatter = new RecordingFormatter
      val config    = Map(
        "views" -> """[
          { "name": "client", "filter": "path-excludes:^/admin/" },
          { "name": "public", "filter": ["tag-includes:public", "path-includes:^/pets"] },
          { "name": "everything" }
        ]"""
      )
      val ran = BaklavaGenerate.generate(config, Seq(call, adminCall), Seq(formatter))

      ran shouldBe true
      formatter.invocations shouldBe List(
        "target/baklava"                  -> Seq(call, adminCall),
        "target/baklava/views/client"     -> Seq(call),
        "target/baklava/views/public"     -> Seq(call),
        "target/baklava/views/everything" -> Seq(call, adminCall)
      )
    }

    it("skips a view that selects no calls, keeping the full output") {
      val formatter     = new RecordingFormatter
      val config        = Map("views" -> """[{ "name": "none", "filter": "tag-includes:nope" }]""")
      val (ran, stderr) = captureStdErr(BaklavaGenerate.generate(config, Seq(call), Seq(formatter)))

      ran shouldBe true
      formatter.invocations shouldBe List("target/baklava" -> Seq(call))
      stderr should include("view 'none' selected 0 of 1")
    }

    it("fails before running any formatter when the views configuration is invalid") {
      val formatter = new RecordingFormatter
      val config    = Map("views" -> """[{ "name": "client", "filter": "by-moon-phase:full" }]""")

      val error = intercept[RuntimeException](BaklavaGenerate.generate(config, Seq(call), Seq(formatter)))
      error.getMessage should include("by-moon-phase:full")
      formatter.invocations shouldBe empty
    }
  }

  describe("BaklavaGenerate.run") {

    def freshDir(): Path = Files.createTempDirectory("baklava-generate")

    it("formats the union of this run's captures and earlier runs' store, and keeps the store") {
      val tmp   = freshDir()
      val store = new BaklavaCallStore(tmp)
      store.write("pl.iterators.baklava.BaklavaGenerateSpec", callAt("/old", "/old", Nil)).get
      store.mergeRunIntoStore().get
      store.write("pl.iterators.baklava.BaklavaCallStoreSpec", callAt("/new", "/new", Nil)).get
      val formatter     = new RecordingFormatter
      val (ran, stderr) = captureStdErr(BaklavaGenerate.run(Map.empty, store, Seq(getClass.getClassLoader), Seq(formatter)))

      ran shouldBe true
      formatter.invocations.map(_._2.map(_.request.symbolicPath)) shouldBe List(Seq("/new", "/old"))
      store.listStoredCalls().size shouldBe 2
      Files.exists(tmp.resolve("calls")) shouldBe false
      stderr should include("merged 1 suite(s) from this run, kept 1 from earlier runs, formatting 2 call(s)")
    }

    it("prunes a stored suite whose class is gone and reports it") {
      val store = new BaklavaCallStore(freshDir())
      store.write("com.acme.Gone", callAt("/gone", "/gone", Nil)).get
      store.write("pl.iterators.baklava.BaklavaGenerateSpec", callAt("/kept", "/kept", Nil)).get
      store.mergeRunIntoStore().get
      val formatter   = new RecordingFormatter
      val (_, stderr) = captureStdErr(BaklavaGenerate.run(Map.empty, store, Seq(getClass.getClassLoader), Seq(formatter)))

      formatter.invocations.map(_._2.map(_.request.symbolicPath)) shouldBe List(Seq("/kept"))
      stderr should include("removed stored calls of 'com.acme.Gone'")
    }

    it("warns instead of pruning when the loaders cannot see test classes") {
      val store = new BaklavaCallStore(freshDir())
      store.write("pl.iterators.baklava.BaklavaGenerateSpec", callAt("/fresh", "/fresh", Nil)).get
      val blind = new ClassLoader(null) {
        override def loadClass(name: String, resolve: Boolean): Class[_] = throw new ClassNotFoundException(name)
      }
      val formatter   = new RecordingFormatter
      val (_, stderr) = captureStdErr(BaklavaGenerate.run(Map.empty, store, Seq(blind), Seq(formatter)))

      formatter.invocations.map(_._2.map(_.request.symbolicPath)) shouldBe List(Seq("/fresh"))
      stderr should include("skipped pruning")
      stderr should include("baklavaClean")
    }

    it("keeps this run's captures in staging when the merge fails, so a rerun can retry") {
      val tmp      = freshDir()
      val store    = new BaklavaCallStore(tmp)
      val captured = store.write("pl.iterators.baklava.BaklavaGenerateSpec", callAt("/staged", "/staged", Nil)).get
      Files.write(tmp.resolve("store"), Array.empty[Byte]) // a plain file where the store directory belongs: every merge fails
      val formatter     = new RecordingFormatter
      val (ran, stderr) = captureStdErr(BaklavaGenerate.run(Map.empty, store, Seq(getClass.getClassLoader), Seq(formatter)))

      ran shouldBe false
      stderr should include("failed to merge")
      Files.exists(captured) shouldBe true
    }

    it("skips generation with the store-empty message when nothing is stored or captured") {
      val formatter     = new RecordingFormatter
      val (ran, stderr) =
        captureStdErr(BaklavaGenerate.run(Map.empty, new BaklavaCallStore(freshDir()), Seq(getClass.getClassLoader), Seq(formatter)))

      ran shouldBe false
      formatter.invocations shouldBe empty
      stderr should include("0")
      stderr should include("testFull")
    }
  }
}
